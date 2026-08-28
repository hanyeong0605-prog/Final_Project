"""실전면접 RAG - 회원이 저장해둔 스펙(목표 직무, 보유 기술, 프로젝트, 자격증, 자기소개서)을
조회해서 질문 생성 프롬프트에 넣을 텍스트 블록으로 조립한다.

job_requirement_retrieval.py(채용공고 요구사항 RAG)와 짝을 이룬다 - 그쪽이 "회사가 무엇을
요구하는가"라면 이쪽은 "회원이 무엇을 갖고 있는가"이고, `스펙+회사` 근거에서는 둘을
build_gap_context()로 대조해서 "보유 근거 확인 / 스펙에서 확인되지 않음"을 만든다. Spring
백엔드와 같은 MySQL을 app.core.db로 직접 읽는 방식도 job_requirement_retrieval과 동일하다 -
새 DB나 새 벡터스토어를 만들지 않고 회원이 이미 입력해둔 데이터를 그대로 재사용한다.

개인정보 취급 - 이 모듈이 읽는 건 회원이 직접 입력해 저장해둔 자기 스펙이지만, 프롬프트에
필요한 짧은 문자열만 담아서 돌려준다. 자기소개서는 원문을 그대로 싣지 않고 앞부분만 잘라
쓰고(_MAX_SELF_INTRODUCTION_CHARS), 조회 결과를 로그로 남기지 않는다. 여기서 만든 텍스트
블록은 어디까지나 Gemini 프롬프트 입력용이고 API 응답으로 그대로 나가면 안 된다.

무료 모의면접은 이 모듈을 쓰지 않는다 - 무료는 목표 직무만 쓰고 프로젝트/기술/자격증/
자기소개서는 질문 생성에 사용하지 않기로 했다(설계 문서 "무료 모의면접" 절). 등급 게이팅은
호출부(router.py)가 mode/source로 하고 이 모듈 자체는 아무 판단도 하지 않는다 -
job_requirement_retrieval과 같은 원칙이다.

member_id가 없으면 DB 연결을 만들지 않고 즉시 None을 반환한다(무료 요청이 실수로 흘러들어와도
쿼리 비용이 들지 않게). 저장된 스펙이 하나도 없거나 조회에 실패해도 None이고, 호출부는 None이면
스펙 근거 없이 진행하거나 사용자에게 스펙 입력을 안내해야 한다. DB 오류로 질문 생성 자체가
막히면 안 되므로 예외를 던지지 않는다(fail-open).
"""

import re
from dataclasses import dataclass, field

from sqlalchemy import text

from app.core.db import get_engine
from app.domain.interview.job_requirement_retrieval import JobRequirementRow, narrow_by_category

# 프롬프트가 너무 길어지지 않도록 상한을 둔다 - 취지는 job_requirement_retrieval의
# _MAX_REQUIREMENTS_IN_PROMPT와 같다(질문 한 개를 만드는 데 필요한 만큼만 넣는다).
_MAX_SKILLS_IN_PROMPT = 12
_MAX_PROJECTS_IN_PROMPT = 3
_MAX_CERTIFICATES_IN_PROMPT = 5
_MAX_PROJECT_FIELD_CHARS = 200
_MAX_SELF_INTRODUCTION_CHARS = 400

# 키워드 대조에 쓸 토큰. 영문/숫자/한글만 남기고 나머지(괄호, 하이픈, 점, 슬래시)는 버린다 -
# "Spring Boot", "Node.js", "CI/CD" 같은 표기 차이를 흡수하기 위함.
_TOKEN_PATTERN = re.compile(r"[a-z0-9가-힣]+")

# 한 글자 토큰은 대조에서 제외한다 - "C", "R"처럼 진짜 기술명일 수도 있지만, 한글 한 글자가
# 요구사항 문장 아무 데나 걸려서 "보유 근거 확인"으로 잘못 표시되는 오탐이 훨씬 크다.
# 확인되지 않은 것을 확인됐다고 말하는 쪽이 반대보다 위험하다는 판단.
_MIN_KEYWORD_LENGTH = 2


@dataclass
class MemberSkillRow:
    name: str
    category: str = ""


@dataclass
class MemberProjectRow:
    title: str
    role_description: str = ""
    problem_description: str = ""
    solution_description: str = ""
    result_description: str = ""


@dataclass
class MemberCertificateRow:
    name: str
    issuer: str = ""


@dataclass
class MemberSpec:
    """질문 생성 프롬프트에 넣을 만큼만 추린 회원 스펙."""

    target_role: str = ""
    skills: list[MemberSkillRow] = field(default_factory=list)
    projects: list[MemberProjectRow] = field(default_factory=list)
    certificates: list[MemberCertificateRow] = field(default_factory=list)
    technical_summary: str = ""
    self_introduction: str = ""

    def has_question_evidence(self) -> bool:
        """`스펙만`/`스펙+회사` 근거로 질문을 만들 수 있을 만큼 내용이 있는지.

        목표 직무만 있는 상태는 근거로 치지 않는다 - 그건 무료 모의면접도 쓰는 값이라
        "직무 이름만 아는 채로 맞춤 질문을 지어내는" 상황이 되기 때문이다. 호출부는 이게
        False면 사용자에게 스펙 입력을 안내해야 한다(설계 문서의 `스펙 입력하기` 흐름).
        """
        return bool(
            self.skills
            or self.projects
            or self.certificates
            or self.technical_summary
            or self.self_introduction
        )


def _clip(value: str | None, limit: int) -> str:
    """None과 줄바꿈/연속 공백을 정리하고 상한을 넘으면 잘라낸다. 잘렸다는 사실을 말줄임표로
    남겨서 Gemini가 끊긴 문장을 원문 그대로로 오해하지 않게 한다."""
    cleaned = " ".join((value or "").split())
    if len(cleaned) <= limit:
        return cleaned
    return cleaned[:limit] + "..."


def fetch_member_spec(member_id: int | None) -> MemberSpec | None:
    """회원이 저장해둔 스펙을 읽어 온다. member_id가 없으면 DB 연결을 만들지 않고 즉시
    None, 저장된 스펙이 하나도 없거나 조회에 실패해도 None이다(fail-open)."""
    if not member_id:
        return None
    try:
        with get_engine().connect() as connection:
            profile = connection.execute(
                text("""
                    SELECT mp.target_role, ms.technical_summary
                    FROM members m
                    LEFT JOIN member_profiles mp ON mp.member_id = m.id
                    LEFT JOIN member_specifications ms ON ms.member_id = m.id
                    WHERE m.id = :member_id
                """),
                {"member_id": member_id},
            ).mappings().first()

            skill_rows = list(connection.execute(
                text("""
                    SELECT s.name, s.category
                    FROM member_skills msk
                    JOIN skills s ON s.id = msk.skill_id
                    WHERE msk.member_id = :member_id
                    ORDER BY s.display_order, s.name
                """),
                {"member_id": member_id},
            ).mappings())

            project_rows = list(connection.execute(
                text("""
                    SELECT title, role_description, problem_description,
                           solution_description, result_description
                    FROM projects
                    WHERE member_id = :member_id
                    ORDER BY COALESCE(ended_at, started_at) DESC, id DESC
                """),
                {"member_id": member_id},
            ).mappings())

            certificate_rows = list(connection.execute(
                text("""
                    SELECT name, issuer
                    FROM certificates
                    WHERE member_id = :member_id
                    ORDER BY acquired_at DESC, id DESC
                """),
                {"member_id": member_id},
            ).mappings())

            # 자기소개서는 대표(is_primary) 한 건만 쓴다 - 여러 건을 다 넣으면 프롬프트만
            # 길어지고 질문 한 개를 만드는 데는 도움이 되지 않는다.
            introduction = connection.execute(
                text("""
                    SELECT content
                    FROM self_introductions
                    WHERE member_id = :member_id
                    ORDER BY is_primary DESC, updated_at DESC
                    LIMIT 1
                """),
                {"member_id": member_id},
            ).scalar()
    except Exception:
        return None

    spec = MemberSpec(
        target_role=_clip(profile["target_role"] if profile else "", 80),
        skills=[
            MemberSkillRow(name=_clip(row["name"], 60), category=_clip(row["category"], 30))
            for row in skill_rows
        ],
        projects=[
            MemberProjectRow(
                title=_clip(row["title"], 120),
                role_description=_clip(row["role_description"], _MAX_PROJECT_FIELD_CHARS),
                problem_description=_clip(row["problem_description"], _MAX_PROJECT_FIELD_CHARS),
                solution_description=_clip(row["solution_description"], _MAX_PROJECT_FIELD_CHARS),
                result_description=_clip(row["result_description"], _MAX_PROJECT_FIELD_CHARS),
            )
            for row in project_rows
        ],
        certificates=[
            MemberCertificateRow(name=_clip(row["name"], 120), issuer=_clip(row["issuer"], 120))
            for row in certificate_rows
        ],
        technical_summary=_clip(
            profile["technical_summary"] if profile else "", _MAX_SELF_INTRODUCTION_CHARS
        ),
        self_introduction=_clip(introduction, _MAX_SELF_INTRODUCTION_CHARS),
    )
    if not spec.target_role and not spec.has_question_evidence():
        return None
    return spec


def build_member_spec_context(
    member_id: int | None,
    category: str = "",
    spec: MemberSpec | None = None,
) -> str | None:
    """프롬프트에 그대로 삽입할 회원 스펙 블록을 만든다.

    이미 조회해둔 spec이 있으면 그걸 쓰고(한 요청에서 gap 대조와 두 번 조회하지 않기 위함),
    없으면 member_id로 새로 읽는다. 쓸 스펙이 없으면 None을 반환한다 - 호출부는 None이면
    스펙 근거 없이 진행해야 한다."""
    resolved = spec if spec is not None else fetch_member_spec(member_id)
    if resolved is None or not resolved.has_question_evidence():
        return None

    lines = ["[회원이 저장한 스펙 - 실제 근거, 본인이 직접 입력한 내용]"]
    if resolved.target_role:
        lines.append(f"- 목표 직무: {resolved.target_role}")

    if resolved.skills:
        names = ", ".join(
            skill.name
            for skill in narrow_by_category(
                resolved.skills,
                category,
                _MAX_SKILLS_IN_PROMPT,
                lambda skill: f"{skill.name} {skill.category}",
            )
        )
        lines.append(f"- 보유 기술: {names}")

    for certificate in narrow_by_category(
        resolved.certificates, category, _MAX_CERTIFICATES_IN_PROMPT, lambda row: row.name
    ):
        issuer = f" ({certificate.issuer})" if certificate.issuer else ""
        lines.append(f"- 자격증: {certificate.name}{issuer}")

    for project in narrow_by_category(
        resolved.projects,
        category,
        _MAX_PROJECTS_IN_PROMPT,
        lambda row: " ".join(
            [
                row.title,
                row.role_description,
                row.problem_description,
                row.solution_description,
                row.result_description,
            ]
        ),
    ):
        lines.append(f"- 프로젝트: {project.title}")
        for label, value in (
            ("역할", project.role_description),
            ("문제", project.problem_description),
            ("해결", project.solution_description),
            ("성과", project.result_description),
        ):
            if value:
                lines.append(f"  {label}: {value}")

    # 자유 서술 필드는 여기서 한 번 더 자른다. fetch_member_spec이 이미 잘라서 돌려주지만,
    # spec을 직접 넘겨받는 경로(호출부가 만들어 준 MemberSpec)에서도 원문이 통째로 프롬프트에
    # 실리지 않는다는 보장이 필요하다 - 길이 제한은 프롬프트 예산 문제이기 전에 개인정보
    # 취급 약속이라서 조회 경로에만 걸어두면 안 된다.
    if resolved.technical_summary:
        lines.append(f"- 기술 요약: {_clip(resolved.technical_summary, _MAX_SELF_INTRODUCTION_CHARS)}")
    if resolved.self_introduction:
        lines.append(f"- 자기소개서 발췌: {_clip(resolved.self_introduction, _MAX_SELF_INTRODUCTION_CHARS)}")
    return "\n".join(lines)


def _keywords(values: list[str]) -> set[str]:
    tokens: set[str] = set()
    for value in values:
        tokens.update(
            token
            for token in _TOKEN_PATTERN.findall(value.lower())
            if len(token) >= _MIN_KEYWORD_LENGTH
        )
    return tokens


def build_gap_context(
    spec: MemberSpec | None, requirements: list[JobRequirementRow]
) -> str | None:
    """`스펙+회사` 근거 전용 - 공고 요구사항 하나하나를 회원 스펙과 대조한 블록을 만든다.

    대조는 정규화된 키워드 일치로만 한다. 일치하면 `보유 근거 확인`, 아니면 결핍으로 단정하지
    않고 `스펙에서 확인되지 않음`으로만 쓴다 - 회원이 실제로는 갖고 있는데 스펙에 입력해두지
    않았을 뿐일 수 있고, 여기서 "없다"고 단정하면 Gemini가 그걸 사실로 전제한 압박 질문을
    만들게 된다(설계 문서의 심리 압박 금지 원칙).

    대조 근거는 보유 기술과 자격증만 쓴다 - 프로젝트 설명 같은 산문까지 넣으면 문장 안의
    흔한 단어가 요구사항에 걸려서 `확인`이 남발된다."""
    if spec is None or not requirements:
        return None

    owned = _keywords(
        [skill.name for skill in spec.skills] + [certificate.name for certificate in spec.certificates]
    )
    if not owned:
        return None

    lines = ["[회원 스펙과 공고 요구사항 대조 - 키워드 일치 여부만 확인한 결과이고 숙련도는 알 수 없음]"]
    for requirement in requirements:
        matched = owned & _keywords([requirement.content])
        if matched:
            lines.append(
                f"- 보유 근거 확인: {requirement.content} (일치 키워드: {', '.join(sorted(matched))})"
            )
        else:
            lines.append(f"- 스펙에서 확인되지 않음: {requirement.content}")
    return "\n".join(lines)
