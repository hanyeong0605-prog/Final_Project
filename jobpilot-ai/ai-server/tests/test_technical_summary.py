"""resume/technical_summary.py 단위 테스트.

self_introduction.py 테스트와 같은 패턴 - Gemini를 실제로 호출하지 않고 fail-open 가드와
프롬프트 조립만 검증한다.
"""

from unittest.mock import patch

from app.domain.resume import technical_summary
from app.domain.resume.technical_summary import TechnicalSummaryResult, synthesize


def test_no_api_key_returns_guidance_message(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "")

    result = synthesize(job="백엔드 개발자", self_introductions=["자기소개서 본문"])

    assert isinstance(result, TechnicalSummaryResult)
    assert result.ok is False
    assert result.message == technical_summary._NO_KEY_MESSAGE
    assert result.summary is None


def test_no_content_returns_guidance_without_calling_api(monkeypatch):
    """자기소개서도 프로젝트도 하나도 없으면(아직 아무것도 안 쓴 회원) Gemini를 아예
    호출하지 않아야 한다."""
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = synthesize(job="백엔드 개발자", self_introductions=[], projects=[])

    assert result.ok is False
    assert result.message == technical_summary._NO_CONTENT_MESSAGE
    mock_client.assert_not_called()


def test_synthesize_returns_gemini_text(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "Spring Boot와 MySQL을 활용해 백엔드를 개발한 경험이 있습니다."

    class FakeModels:
        def generate_content(self, model, contents):
            assert "자기소개서 본문" in contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = synthesize(job="백엔드 개발자", self_introductions=["자기소개서 본문"])

    assert result.ok is True
    assert result.summary == "Spring Boot와 MySQL을 활용해 백엔드를 개발한 경험이 있습니다."


def test_synthesize_includes_project_fields_in_prompt(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = "요약"

    class FakeModels:
        def generate_content(self, model, contents):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    projects = [
        {
            "title": "커머스 플랫폼",
            "role_description": "백엔드 API 설계",
            "problem_description": "동시성 이슈",
            "solution_description": "낙관적 락 적용",
            "result_description": "응답 지연 30% 개선",
        }
    ]

    with patch("google.genai.Client", FakeClient):
        synthesize(job="백엔드 개발자", projects=projects)

    assert "커머스 플랫폼" in captured["prompt"]
    assert "낙관적 락 적용" in captured["prompt"]
    assert "응답 지연 30% 개선" in captured["prompt"]


def test_existing_summary_included_as_reference_only(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = "요약"

    class FakeModels:
        def generate_content(self, model, contents):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        synthesize(job="백엔드 개발자", existing_summary="기존 요약 문장", self_introductions=["새 자기소개서"])

    assert "기존 요약 문장" in captured["prompt"]
    assert "그대로 베끼지 말고" in captured["prompt"]


def test_synthesize_api_failure_is_fail_open(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client", side_effect=RuntimeError("network down")):
        result = synthesize(job="백엔드 개발자", self_introductions=["자기소개서 본문"])

    assert result.ok is False
    assert result.summary is None


def test_synthesize_empty_gemini_response_is_fail_open(monkeypatch):
    monkeypatch.setattr(technical_summary.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "   "

    class FakeModels:
        def generate_content(self, model, contents):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = synthesize(job="백엔드 개발자", self_introductions=["자기소개서 본문"])

    assert result.ok is False
    assert result.message == technical_summary._EMPTY_RESULT_MESSAGE
