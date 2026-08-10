"""개인 타임라인 - 누적 인사이트 API. resume 도메인과 같은 이유로 공개 엔드포인트다(프론트가
회원의 세션 요약 + 이력서 내용을 이미 갖고 있어서 그대로 넘겨주면 되고, 별도 내부 인증이
필요 없다 - resume/router.py의 self-introduction/project 엔드포인트와 동일한 판단)."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.domain.timeline.insight import generate_insight

router = APIRouter()


class SessionSummaryInput(BaseModel):
    role: str = ""
    interview_type: str = ""
    overall_score: int | None = None
    improvements: list[str] = []


class ProjectContentInput(BaseModel):
    title: str = ""
    role_description: str = ""
    problem_description: str = ""
    solution_description: str = ""
    result_description: str = ""


class GenerateInsightRequest(BaseModel):
    sessions: list[SessionSummaryInput] = []
    self_introductions: list[str] = []
    projects: list[ProjectContentInput] = []


@router.post("/insight/generate")
def generate_timeline_insight(body: GenerateInsightRequest):
    result = generate_insight(
        sessions=[s.model_dump() for s in body.sessions],
        self_introductions=body.self_introductions,
        projects=[p.model_dump() for p in body.projects],
    )
    return result.to_dict()
