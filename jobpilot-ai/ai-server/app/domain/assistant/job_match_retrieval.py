"""로그인 회원 전용 채용공고 챗봇 근거 조회.

챗봇은 공고 전체를 임의 검색하거나 새 적합도를 계산하지 않는다. Spring 매칭 도메인이
산출한 본인 회원의 결과만 읽고, 아직 모집 중인 공고만 제공해 LLM은 근거를 설명하게 한다.
"""

from dataclasses import dataclass
from datetime import datetime

from sqlalchemy import text

from app.core.db import get_engine

_MAX_MATCHES = 3
_MAX_TEXT_CHARS = 280
_JOB_INTENT_KEYWORDS = (
    "채용", "공고", "추천", "지원", "구직", "일자리", "취업처", "포지션",
)


@dataclass(frozen=True)
class JobMatchReference:
    job_posting_id: int
    company_name: str
    title: str
    source_url: str
    location: str = ""
    deadline_at: datetime | None = None
    readiness_score: float = 0.0
    recommendation_level: str = ""
    summary_comment: str = ""
    missing_required_count: int = 0

    def to_dict(self) -> dict:
        return {
            "job_posting_id": self.job_posting_id,
            "company_name": self.company_name,
            "title": self.title,
            "source_url": self.source_url,
            "location": self.location,
            "deadline_at": self.deadline_at.isoformat() if self.deadline_at else None,
            "readiness_score": self.readiness_score,
            "recommendation_level": self.recommendation_level,
            "summary_comment": self.summary_comment,
            "missing_required_count": self.missing_required_count,
        }


def _clip(value: object, limit: int = _MAX_TEXT_CHARS) -> str:
    cleaned = " ".join(str(value or "").split())
    return cleaned if len(cleaned) <= limit else cleaned[: limit - 1] + "…"


def is_job_question(message: str) -> bool:
    """공고/지원 의도일 때만 개인 매칭을 조회한다."""
    normalized = (message or "").replace(" ", "").lower()
    return any(keyword in normalized for keyword in _JOB_INTENT_KEYWORDS)


def fetch_active_matches(member_id: int | None, limit: int = _MAX_MATCHES) -> list[JobMatchReference]:
    if not member_id:
        return []
    safe_limit = max(1, min(limit, _MAX_MATCHES))
    try:
        with get_engine().connect() as connection:
            rows = connection.execute(
                text("""
                    SELECT jp.id AS job_posting_id, jp.company_name, jp.title, jp.source_url,
                           jp.location, jp.deadline_at, jm.readiness_score,
                           jm.recommendation_level, jm.summary_comment,
                           jm.missing_required_count
                    FROM job_matches jm
                    JOIN job_postings jp ON jp.id = jm.job_posting_id
                    WHERE jm.member_id = :member_id
                      AND jp.status = 'ACTIVE'
                      AND (jp.deadline_at IS NULL OR jp.deadline_at >= NOW())
                    ORDER BY
                      CASE jm.recommendation_level
                        WHEN 'APPLY_NOW' THEN 0
                        WHEN 'PREPARE_MORE' THEN 1
                        ELSE 2
                      END,
                      jm.missing_required_count ASC,
                      jm.readiness_score DESC,
                      jp.deadline_at IS NULL,
                      jp.deadline_at ASC
                    LIMIT :limit
                """),
                {"member_id": member_id, "limit": safe_limit},
            ).mappings()
            return [
                JobMatchReference(
                    job_posting_id=int(row["job_posting_id"]),
                    company_name=_clip(row["company_name"], 120),
                    title=_clip(row["title"], 160),
                    source_url=_clip(row["source_url"], 1500),
                    location=_clip(row["location"], 80),
                    deadline_at=row["deadline_at"],
                    readiness_score=float(row["readiness_score"] or 0),
                    recommendation_level=_clip(row["recommendation_level"], 40),
                    summary_comment=_clip(row["summary_comment"]),
                    missing_required_count=int(row["missing_required_count"] or 0),
                )
                for row in rows
            ]
    except Exception:
        # 공고 DB 장애가 일반 챗봇 응답까지 막아서는 안 된다.
        return []


def job_matches_prompt_block(matches: list[JobMatchReference]) -> str:
    if not matches:
        return ""
    lines = [
        "[현재 회원의 모집 중 매칭 공고 - 시스템이 계산한 결과이며 이 목록 밖 공고를 추천하지 말 것]"
    ]
    for match in matches:
        deadline = match.deadline_at.strftime("%Y-%m-%d") if match.deadline_at else "상시/마감일 미정"
        lines.append(
            f"- 공고 ID {match.job_posting_id}: {match.company_name} | {match.title} | "
            f"적합도 {match.readiness_score:.0f}점 | 단계 {match.recommendation_level} | "
            f"미확인 필수요건 {match.missing_required_count}개 | 지역 {match.location or '미정'} | 마감 {deadline}"
        )
        if match.summary_comment:
            lines.append(f"  시스템 매칭 설명: {match.summary_comment}")
    return "\n".join(lines)
