"""timeline/insight.py 단위 테스트 - self_introduction.py 테스트와 같은 패턴."""

from unittest.mock import patch

from app.domain.timeline import insight as insight_module
from app.domain.timeline.insight import TimelineInsight, generate_insight


def _session(score=3, improvements=None, role="BACKEND", interview_type="역량면접"):
    return {"role": role, "interview_type": interview_type, "overall_score": score, "improvements": improvements or []}


def test_no_api_key_returns_guidance_message(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "")

    result = generate_insight(sessions=[_session(), _session()])

    assert isinstance(result, TimelineInsight)
    assert result.ok is False
    assert result.message == insight_module._NO_KEY_MESSAGE


def test_fewer_than_two_sessions_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = generate_insight(sessions=[_session()])

    assert result.ok is False
    assert result.message == insight_module._NOT_ENOUGH_SESSIONS_MESSAGE
    mock_client.assert_not_called()


def test_zero_sessions_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = generate_insight(sessions=[])

    assert result.ok is False
    mock_client.assert_not_called()


def test_generate_insight_returns_parsed_result(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = (
            '{"recurring_points": ["두괄식으로 답하는 연습이 필요함"], '
            '"resume_linked_suggestion": "프로젝트 A의 성과를 답변에 활용해보세요."}'
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    sessions = [
        _session(improvements=["결론부터 말하기"]),
        _session(improvements=["결론부터 말하기", "구체적 수치 언급"]),
    ]

    with patch("google.genai.Client", FakeClient):
        result = generate_insight(sessions=sessions, self_introductions=["자기소개서 본문"])

    assert result.ok is True
    assert result.recurring_points == ["두괄식으로 답하는 연습이 필요함"]
    assert result.resume_linked_suggestion == "프로젝트 A의 성과를 답변에 활용해보세요."


def test_prompt_includes_session_and_resume_content(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = '{"recurring_points": [], "resume_linked_suggestion": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    projects = [{"title": "커머스 플랫폼", "role_description": "백엔드 개발", "problem_description": "",
                 "solution_description": "", "result_description": ""}]

    with patch("google.genai.Client", FakeClient):
        generate_insight(sessions=[_session(improvements=["A"]), _session(improvements=["A"])], projects=projects)

    assert "커머스 플랫폼" in captured["prompt"]
    assert "백엔드 개발" in captured["prompt"]


def test_api_failure_is_fail_open(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client", side_effect=RuntimeError("network down")):
        result = generate_insight(sessions=[_session(), _session()])

    assert result.ok is False
    assert result.recurring_points == []


def test_unparseable_response_is_fail_open(monkeypatch):
    monkeypatch.setattr(insight_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "JSON이 아님"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = generate_insight(sessions=[_session(), _session()])

    assert result.ok is False
    assert result.message == insight_module._PARSE_FAIL_MESSAGE
