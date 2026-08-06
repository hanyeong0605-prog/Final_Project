"""NextQuestionRequest.wants_personalized() 단위 테스트.

/next-question이 LoRA(generate_question) vs Gemini 맞춤 질문(generate_personalized_question)
경로 중 어디를 탈지 결정하는 조건 - "분야만 선택해도 맞춤 질문이 나오게" 요청으로 job이
기본값이 아니기만 해도(기술 요약 없이도) 트리거되도록 완화한 부분을 검증한다.
"""

from app.domain.interview.question_generator import DEFAULT_JOB
from app.domain.interview.router import NextQuestionRequest


def test_no_info_uses_default_lora_path():
    body = NextQuestionRequest()
    assert body.wants_personalized() is False


def test_default_job_with_tech_summary_wants_personalized():
    body = NextQuestionRequest(tech_summary="Spring Boot, JPA 프로젝트")
    assert body.wants_personalized() is True


def test_selected_field_alone_wants_personalized():
    """분야만 고르고 기술 요약은 없는 경우(프로필 미입력 사용자가 시작 화면에서 "백엔드"만
    선택) - job이 DEFAULT_JOB이 아니면 그것만으로도 맞춤 질문 경로를 타야 한다."""
    body = NextQuestionRequest(job="백엔드")
    assert body.wants_personalized() is True


def test_default_job_no_tech_summary_uses_lora_path():
    body = NextQuestionRequest(job=DEFAULT_JOB, tech_summary="")
    assert body.wants_personalized() is False


def test_blank_job_uses_lora_path():
    """공백만 있는 job은 실질적으로 정보가 없는 것과 같으므로 LoRA 경로를 타야 한다."""
    body = NextQuestionRequest(job="   ", tech_summary="")
    assert body.wants_personalized() is False
