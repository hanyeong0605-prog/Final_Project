"""이력서 작성 도우미 API.

프론트가 기존 GET /api/v1/members/me/career-profile로 목표직무/기술요약을 먼저 읽어와서
job/tech_summary로 넘겨주면, 이 라우터는 그걸 컨텍스트로 써서 생성/첨삭만 하고 저장은 하지
않는다 - 프론트가 결과를 받아서 기존 backend CRUD(POST/PUT /api/v1/members/me/
self-introductions, /projects)를 별도로 호출해서 저장한다(self_introduction.py 모듈
docstring 참고).
"""

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app.core.config import settings
from app.domain.resume import project as project_module
from app.domain.resume import technical_summary as technical_summary_module
from app.domain.resume import document_ai as document_ai_module
from app.domain.resume.self_introduction import (
    GUIDED_QUESTIONS,
    critique as critique_self_introduction,
    generate_draft as generate_self_introduction_draft,
    parse_company_questions,
)

router = APIRouter()


def _verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    """스프링만 부를 수 있는 내부 엔드포인트 검증 - crawler/router.py의 같은 함수와 동일한
    개념/같은 키(.env의 INTERNAL_API_KEY)를 쓴다. 이 엔드포인트는 프론트가 직접 부르지 않고
    Spring(ResumeCareerSyncService)이 자기소개서/프로젝트 저장 시 서버 간 호출로만 부른다."""
    if not settings.internal_api_key or x_internal_api_key != settings.internal_api_key:
        raise HTTPException(status_code=401, detail="internal api key가 없거나 올바르지 않습니다.")


class AnalyzeResumeDocumentRequest(BaseModel):
    text: str = ""


@router.post("/document/analyze")
def analyze_resume_document(body: AnalyzeResumeDocumentRequest, x_internal_api_key: str | None = Header(default=None)):
    _verify_internal_api_key(x_internal_api_key)
    return document_ai_module.analyze_profile(body.text).to_dict()


class GenerateResumeDocumentRequest(BaseModel):
    profile: dict[str, object] = Field(default_factory=dict)
    answers: list[str] = Field(default_factory=list)
    template_key: str = "STANDARD"
    template_hint: str = ""


@router.post("/document/generate")
def generate_resume_document(body: GenerateResumeDocumentRequest, x_internal_api_key: str | None = Header(default=None)):
    _verify_internal_api_key(x_internal_api_key)
    return document_ai_module.generate_document_draft(
        profile=body.profile,
        answers=body.answers,
        template_key=body.template_key,
        template_hint=body.template_hint,
    ).to_dict()


@router.get("/self-introduction/questions")
def self_introduction_questions():
    return {"questions": list(GUIDED_QUESTIONS)}


class GenerateSelfIntroductionRequest(BaseModel):
    job: str = ""
    tech_summary: str = ""
    # questions와 같은 순서/길이로 넘겨야 한다 - 안 쓴 항목은 빈 문자열로 채우면 됨.
    answers: list[str] = []
    # 2026-08-13: 비워두면 GUIDED_QUESTIONS(기본 4문항)를 쓴다 - 회사 양식을 파싱했다면
    # (아래 /self-introduction/parse-questions 참고) 그 결과를 그대로 answers와 같은
    # 순서로 넘기면 그 문항 기준으로 초안이 만들어진다.
    questions: list[str] = []
    # 2026-08-14: "이력서 파일 미리 들고 오면 스캔해서 활용" 요청 - 프론트가 기존 백엔드
    # 이력서 업로드/추출 API(ResumeDocumentService.extract)로 받은 extractedText를 그대로
    # 넘기면, 답변을 다듬을 때 배경 참고자료로만 쓴다(self_introduction._career_context 참고 -
    # 답변에 없는 새 사실의 출처로는 쓰지 않는다).
    resume_context: str = ""


@router.post("/self-introduction/generate")
def generate_self_introduction(body: GenerateSelfIntroductionRequest):
    draft = generate_self_introduction_draft(
        job=body.job,
        tech_summary=body.tech_summary,
        answers=body.answers,
        questions=body.questions or None,
        resume_context=body.resume_context,
    )
    return draft.to_dict()


class ParseCompanyQuestionsRequest(BaseModel):
    raw_text: str = ""


# 2026-08-13: "회사 자소서 양식이 있으면 그거에 대해 필요한 질문 물어보고" 요청으로 추가 -
# 채용 사이트에서 그대로 복사한 문항 안내 텍스트를 받아 실제 질문 목록만 추출한다
# (self_introduction.parse_company_questions 참고). 프론트는 이 결과를 GUIDED_QUESTIONS
# 대신 채팅형 질문 목록으로 쓴다.
@router.post("/self-introduction/parse-questions")
def parse_self_introduction_company_questions(body: ParseCompanyQuestionsRequest):
    result = parse_company_questions(body.raw_text)
    return result.to_dict()


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
