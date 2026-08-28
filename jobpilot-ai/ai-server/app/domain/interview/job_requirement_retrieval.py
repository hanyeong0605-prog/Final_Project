"""모의면접 RAG - 사용자가 선택한 채용공고의 요구사항(job_requirements)을 조회해서
질문 생성/모범답안/채점 프롬프트에 넣을 텍스트 블록으로 조립한다.

Spring 백엔드가 쓰는 것과 같은 MySQL을 SQLAlchemy로 직접 조회한다(app.core.db, matching/
service.py가 이미 쓰던 패턴을 공유) - 새 DB, 새 벡터스토어를 만들지 않고 채용공고 크롤링/
요구사항 추출 파이프라인(JobRequirementExtractionService)이 이미 만들어둔 데이터를 그대로
재사용한다.

무료/유료 등급과 무관하게 이 모듈 자체는 아무 게이팅도 하지 않는다 - 호출부(router.py)가
유료 사용자에게만 job_posting_id를 받아서 넘기는 식으로 게이팅한다. job_posting_id가 없거나
조회 결과가 비어 있으면 None을 반환해서, 호출부가 기존과 동일하게(RAG 없이) 동작하도록
한다 - 이 기능은 선택 사항이지 필수 단계가 아니다.
"""

from collections.abc import Callable
from dataclasses import dataclass
from typing import TypeVar

from sqlalchemy import text

from app.core.db import get_engine

_T = TypeVar("_T")

# 프롬프트가 너무 길어지지 않도록 상한을 둔다 - 공고 하나당 요구사항은 추출 시점에 이미
# 최대 15개로 제한돼 있지만(OpenAiJobRequirementClient.MAX_REQUIREMENTS), 그중에서도
# 지금 질문 카테고리와 가장 관련 있는 것만 추려서 넣는다.
_MAX_REQUIREMENTS_IN_PROMPT = 6


@dataclass
class JobRequirementRow:
    type: str
    content: str
    importance: str
    source_excerpt: str
    verification_status: str


def _fetch_rows(job_posting_id: int) -> tuple[str | None, str | None, list[JobRequirementRow]]:
    """(공고 제목, 회사명, 요구사항 목록)을 반환한다. 공고가 없거나 아직 요구사항이
    추출되지 않았으면 (None, None, [])."""
    statement = text("""
        SELECT jp.title, jp.company_name, jr.type, jr.content, jr.importance,
               jr.source_excerpt, jr.verification_status
        FROM job_requirements jr
        JOIN job_postings jp ON jp.id = jr.job_posting_id
        WHERE jr.job_posting_id = :job_posting_id
        ORDER BY (jr.verification_status = 'VERIFIED') DESC,
                 (jr.importance = 'REQUIRED') DESC
    """)
    with get_engine().connect() as connection:
        rows = list(connection.execute(statement, {"job_posting_id": job_posting_id}).mappings())

    if not rows:
        return None, None, []

    title = rows[0]["title"]
    company_name = rows[0]["company_name"]
    requirements = [
        JobRequirementRow(
            type=row["type"],
            content=row["content"],
            importance=row["importance"],
            source_excerpt=row["source_excerpt"],
            verification_status=row["verification_status"],
        )
        for row in rows
    ]
    return title, company_name, requirements


def narrow_by_category(
    items: list[_T], category: str, limit: int, key: Callable[[_T], str]
) -> list[_T]:
    """항목이 상한보다 많으면 question_similarity.py와 같은 방식(TF-IDF, char n-gram,
    외부 API 호출 없음)으로 지금 질문 카테고리와 가장 겹치는 것만 추린다. 상한 이하거나
    카테고리가 없으면(예: 세션 전체를 대상으로 하는 evaluate-session) 앞에서 자른 상위 N개를
    그대로 쓴다 - 호출부가 이미 의미 있는 순서로 정렬해뒀다고 본다(_fetch_rows는
    VERIFIED/REQUIRED 우선).

    원래 요구사항 전용(_narrow_by_category)이었는데 member_spec_retrieval도 회원 스펙
    (기술/자격증/프로젝트)을 같은 방식으로 추려야 해서 제네릭으로 빼고 공개했다 - 추리는
    기준이 두 군데서 갈라지면 프롬프트 길이 통제가 서로 어긋난다."""
    if len(items) <= limit or not category:
        return items[:limit]

    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity

    texts = [key(item) for item in items]
    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4))
    matrix = vectorizer.fit_transform([*texts, category])
    similarities = cosine_similarity(matrix[-1], matrix[:-1])[0]
    ranked = sorted(zip(items, similarities), key=lambda pair: pair[1], reverse=True)
    return [item for item, _ in ranked[:limit]]


def fetch_narrowed_requirements(
    job_posting_id: int | None, category: str = ""
) -> list[JobRequirementRow]:
    """build_job_requirements_context와 같은 조회·추림을 거친 요구사항 행을 그대로 돌려준다.

    `스펙+회사` 근거에서 회원 스펙과 대조하려면(member_spec_retrieval.build_gap_context)
    완성된 텍스트 블록이 아니라 행 자체가 필요해서 분리했다. 공고를 안 골랐거나 조회에
    실패하면 빈 리스트다 - 여기서도 fail-open이라 호출부는 빈 리스트를 "대조할 게 없음"으로
    다루면 된다."""
    if not job_posting_id:
        return []
    try:
        _, _, requirements = _fetch_rows(job_posting_id)
    except Exception:
        return []
    if not requirements:
        return []
    return narrow_by_category(
        requirements, category, _MAX_REQUIREMENTS_IN_PROMPT, lambda requirement: requirement.content
    )


def build_job_requirements_context(job_posting_id: int | None, category: str = "") -> str | None:
    """프롬프트에 그대로 삽입할 텍스트 블록을 만든다. job_posting_id가 없거나(=공고를 안
    골랐거나 무료 등급이라 애초에 안 넘어옴), 조회에 실패하거나, 그 공고에 추출된 요구사항이
    아직 없으면 None을 반환한다 - 호출부는 None이면 기존과 동일하게(RAG 없이) 동작해야 한다.
    DB 연결 문제 등 예상 못 한 오류로 질문 생성 자체가 막히면 안 되므로 예외를 던지지
    않는다(fail-open)."""
    if not job_posting_id:
        return None
    try:
        title, company_name, requirements = _fetch_rows(job_posting_id)
        if not requirements:
            return None
        narrowed = narrow_by_category(
            requirements, category, _MAX_REQUIREMENTS_IN_PROMPT, lambda requirement: requirement.content
        )

        label = f"{company_name or '해당 회사'} - {title or '지원 공고'}"
        lines = [f"[{label} 요구사항 - 실제 근거, 공고 원문에서 추출됨]"]
        for requirement in narrowed:
            lines.append(f"- ({requirement.importance} · {requirement.type}) {requirement.content}")
            lines.append(f'  원문: "{requirement.source_excerpt}"')
        return "\n".join(lines)
    except Exception:
        return None
