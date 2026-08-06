"""generate_personalized_question() 단위 테스트.

LoRA 모델(generate_question)은 GPU/모델 파일이 필요해 여기서 다루지 않는다(기존 동작
그대로 유지, 이 파일에서 건드리지 않았음). generate_personalized_question은 Gemini만
호출하므로 evaluation.py 테스트와 같은 패턴(google.genai.Client 모킹)으로 검증한다.
"""

from unittest.mock import patch

from app.domain.interview import question_generator
from app.domain.interview.question_generator import generate_personalized_question


def test_no_api_key_returns_none(monkeypatch):
    """GEMINI_API_KEY가 없으면 호출부(router.py)가 LoRA 경로로 폴백해야 하므로 None을
    반환해야 한다 - 예외를 던지면 안 된다(fail-open)."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")
    assert generate_personalized_question(job="백엔드 개발자", tech_summary="   ") is None


def test_empty_tech_summary_returns_none(monkeypatch):
    """스펙(기술 요약)이 없는 사용자는 애초에 이 함수를 부를 이유가 없지만, 방어적으로
    빈 값이 오면 Gemini를 호출하지 않고 바로 None을 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "")
    assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring, JPA") is None


def test_success_returns_question_reflecting_tech_summary(monkeypatch):
    """정상 응답이면 Gemini가 만든 질문 문자열을 그대로 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(question_generator.settings, "gemini_model", "gemini-test")

    captured = {}

    class FakeResponse:
        text = "Spring Boot에서 JPA N+1 문제를 어떻게 해결해보셨나요?"

    class FakeModels:
        def generate_content(self, model, contents):
            captured["model"] = model
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        question = generate_personalized_question(
            job="백엔드 개발자", tech_summary="Spring Boot, JPA로 커머스 프로젝트 진행", category="기술_직무역량"
        )

    assert question == "Spring Boot에서 JPA N+1 문제를 어떻게 해결해보셨나요?"
    assert captured["model"] == "gemini-test"
    assert "백엔드 개발자" in captured["prompt"]
    assert "Spring Boot, JPA로 커머스 프로젝트 진행" in captured["prompt"]
    assert "카테고리: 기술_직무역량" in captured["prompt"]


def test_gemini_failure_returns_none(monkeypatch):
    """Gemini 호출 자체가 예외를 던져도(네트워크 오류 등) 호출부가 LoRA로 폴백할 수
    있도록 예외를 삼키고 None을 반환해야 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    class FakeModels:
        def generate_content(self, model, contents):
            raise RuntimeError("network down")

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring") is None


def test_discardable_empty_response_returns_none(monkeypatch):
    """Gemini가 빈 문자열을 반환하면(드묾) None을 반환해서 LoRA 폴백을 타게 한다."""
    monkeypatch.setattr(question_generator.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "   "

    class FakeModels:
        def generate_content(self, model, contents):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        assert generate_personalized_question(job="백엔드 개발자", tech_summary="Spring") is None
