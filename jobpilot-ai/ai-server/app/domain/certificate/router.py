"""추천 자격증 맞춤 학습 계획 API. timeline/router.py와 같은 이유로 공개 엔드포인트다
(프론트가 이미 회원의 프로필/기술/자격증 정보를 갖고 있어 그대로 넘겨주면 된다)."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.domain.certificate.study_plan import generate_study_plan

router = APIRouter()


class GenerateStudyPlanRequest(BaseModel):
    certificate_name: str
    qualification_type: str = ""
    field_name: str = ""
    sub_field: str = ""
    target_job_family: str = ""
    target_role: str = ""
    skills: list[str] = []
    owned_certificates: list[str] = []


@router.post("/study-plan/generate")
def generate_certificate_study_plan(body: GenerateStudyPlanRequest):
    result = generate_study_plan(
        certificate_name=body.certificate_name,
        qualification_type=body.qualification_type,
        field_name=body.field_name,
        sub_field=body.sub_field,
        target_job_family=body.target_job_family,
        target_role=body.target_role,
        skills=body.skills,
        owned_certificates=body.owned_certificates,
    )
    return result.to_dict()
