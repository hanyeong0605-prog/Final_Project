"""이력서 작성 도우미 API.

프론트가 기존 GET /api/v1/members/me/career-profile로 목표직무/기술요약을 먼저 읽어와서
job/tech_summary로 넘겨주면, 이 라우터는 그걸 컨텍스트로 써서 생성/첨삭만 하고 저장은 하지
않는다 - 프론트가 결과를 받아서 기존 backend CRUD(POST/PUT /api/v1/members/me/
self-introductions, /projects)를 별도로 호출해서 저장한다(self_introduction.py 모듈
docstring 참고).
"""

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

from app.core.config import settings
from app.domain.resume import project as project_module
from app.domain.resume import technical_summary as technical_summary_module
from app.domain.resume.self_introduction import (
    GUIDED_QUESTIONS,
    critique as critique_self_introduction,
    generate_draft as generate_self_introduction_draft,
)

router = APIRouter()


def _verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    """스프링만 부를 수 있는 내부 엔드포인트 검증 - crawler/router.py의 같은 함수와 동일한
    개념/같은 키(.env의 INTERNAL_API_KEY)를 쓴다. 이 엔드포인트는 프론트가 직접 부르지 않고
    Spring(ResumeCareerSyncService)이 자기소개서/프로젝트 저장 시 서버 간 호출로만 부른다."""
    if not settings.internal_api_key or x_internal_api_key != settings.internal_api_key:
        raise HTTPException(status_code=401, detail="internal api key가 없거나 올바르지 않습니다.")


@router.get("/self-introduction/questions")
def self_introduction_questions():
    return {"questions": list(GUIDED_QUESTIONS)}


class GenerateSelfIntroductionRequest(BaseModel):
    job: str = ""
    tech_summary: str = ""
    # GUIDED_QUESTIONS와 같은 순서/길이로 넘겨야 한다 - 안 쓴 항목은 빈 문자열로 채우면 됨.
    answers: list[str] = []


@router.post("/self-introduction/generate")
def generate_self_introduction(body: GenerateSelfIntroductionRequest):
    draft = generate_self_introduction_draft(job=body.job, tech_summary=body.tech_summary, answers=body.answers)
    return draft.to_dict()


class CritiqueSelfIntroductionRequest(BaseModel):
    content: str
    job: str = ""
    tech_summary: str = ""


@router.post("/self-introduction/critique")
def critique_self_introduction_endpoint(body: CritiqueSelfIntroductionRequest):
    result = critique_self_introduction(content=body.content, job=body.job, tech_summary=body.tech_summary)
    return result.to_dict()


@router.get("/project/questions")
def project_questions():
    return {"questions": list(project_module.GUIDED_QUESTIONS)}


class GenerateProjectRequest(BaseModel):
    title: str = ""
    job: str = ""
    tech_summary: str = ""
    # project.GUIDED_QUESTIONS(STAR 4개)와 같은 순서/길이로 넘겨야 한다.
    answers: list[str] = []


@router.post("/project/generate")
def generate_project(body: GenerateProjectRequest):
    draft = project_module.generate_draft(
        title=body.title, job=body.job, tech_summary=body.tech_summary, answers=body.answers
    )
    return draft.to_dict()


class CritiqueProjectRequest(BaseModel):
    role_description: str = ""
    problem_description: str = ""
    solution_description: str = ""
    result_description: str = ""
    job: str = ""
    tech_summary: str = ""


@router.post("/project/critique")
def critique_project(body: CritiqueProjectRequest):
    result = project_module.critique(
        role_description=body.role_description,
        problem_description=body.problem_description,
        solution_description=body.solution_description,
        result_description=body.result_description,
        job=body.job,
        tech_summary=body.tech_summary,
    )
    return result.to_dict()


class TechnicalSummaryProject(BaseModel):
    title: str = ""
    role_description: str = ""
    problem_description: str = ""
    solution_description: str = ""
    result_description: str = ""


class SynthesizeTechnicalSummaryRequest(BaseModel):
    job: str = ""
    existing_summary: str = ""
    self_introductions: list[str] = []
    projects: list[TechnicalSummaryProject] = []


# 2026-08-10: 태스크 #63 "반영" 방향 - Spring이 자기소개서/프로젝트를 저장할 때마다 그
# 회원의 자기소개서 전문 + 프로젝트 STAR 필드를 모두 모아서 이 엔드포인트로 넘기면, 새
# 기술 요약 한 문단을 합성해서 돌려준다(저장은 여전히 Spring 책임 - 여기선 생성만).
@router.post("/technical-summary/synthesize")
def synthesize_technical_summary(
    body: SynthesizeTechnicalSummaryRequest, x_internal_api_key: str | None = Header(default=None)
):
    _verify_internal_api_key(x_internal_api_key)
    result = technical_summary_module.synthesize(
        job=body.job,
        existing_summary=body.existing_summary,
        self_introductions=body.self_introductions,
        projects=[p.model_dump() for p in body.projects],
    )
    return result.to_dict()
