"""resume/self_introduction.py 단위 테스트.

Gemini API를 실제로 호출하지 않는다 - 키 없을 때의 fail-open 동작, 빈 답변/빈 내용 가드,
JSON 파싱 방어 로직만 확인한다(evaluation.py 테스트와 같은 패턴).
"""

from unittest.mock import patch

from app.domain.resume import self_introduction
from app.domain.resume.self_introduction import (
    GUIDED_QUESTIONS,
    SelfIntroductionCritique,
    SelfIntroductionDraft,
    critique,
    generate_draft,
)


def test_no_api_key_returns_guidance_message_for_generate(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "")

    draft = generate_draft(job="백엔드 개발자", answers=["지원 동기입니다"])

    assert isinstance(draft, SelfIntroductionDraft)
    assert draft.ok is False
    assert draft.message == self_introduction._NO_KEY_MESSAGE
    assert draft.content is None


def test_no_answers_returns_guidance_without_calling_api(monkeypatch):
    """답변이 전부 빈 문자열이면(질문식 작성을 시작만 하고 아무것도 안 쓴 경우) Gemini를
    아예 호출하지 않고 안내 메시지만 반환해야 한다."""
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        draft = generate_draft(job="백엔드 개발자", answers=["", "   ", ""])

    assert draft.ok is False
    assert draft.message == self_introduction._NO_ANSWER_MESSAGE
    mock_client.assert_not_called()


def test_generate_draft_returns_gemini_text(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "완성된 자기소개서 본문입니다."

    class FakeModels:
        def generate_content(self, model, contents):
            assert GUIDED_QUESTIONS[0] in contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        draft = generate_draft(job="백엔드 개발자", tech_summary="Spring", answers=["새로운 도전을 하고 싶어서입니다"])

    assert draft.ok is True
    assert draft.content == "완성된 자기소개서 본문입니다."


def test_generate_draft_only_includes_answered_questions(monkeypatch):
    """일부 질문만 답했으면(나머지는 빈 문자열) 프롬프트에 답한 질문/답변만 들어가야
    한다 - 빈 답변을 그대로 프롬프트에 넣으면 Gemini가 빈 내용을 지어낼 위험이 있다."""
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = "본문"

    class FakeModels:
        def generate_content(self, model, contents):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        generate_draft(job="백엔드", answers=["첫 번째 답변", "", "", "네 번째 답변"])

    assert GUIDED_QUESTIONS[0] in captured["prompt"]
    assert "첫 번째 답변" in captured["prompt"]
    assert GUIDED_QUESTIONS[1] not in captured["prompt"]
    assert GUIDED_QUESTIONS[3] in captured["prompt"]
    assert "네 번째 답변" in captured["prompt"]


def test_generate_draft_api_failure_is_fail_open(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client", side_effect=RuntimeError("network down")):
        draft = generate_draft(job="백엔드", answers=["답변"])

    assert draft.ok is False
    assert draft.content is None


def test_no_api_key_returns_guidance_message_for_critique(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "")

    result = critique(content="자기소개서 본문")

    assert isinstance(result, SelfIntroductionCritique)
    assert result.ok is False
    assert result.message == self_introduction._NO_KEY_MESSAGE


def test_empty_content_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = critique(content="   ")

    assert result.ok is False
    assert result.message == self_introduction._NO_CONTENT_MESSAGE
    mock_client.assert_not_called()


def test_critique_parses_structured_response(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = (
            '{"strengths": ["구체적인 사례를 들었다"], '
            '"improvements": ["문장이 너무 길다", "결론이 약하다"], '
            '"revised_example": "다시 쓴 문단입니다"}'
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = critique(content="자기소개서 본문", job="백엔드 개발자")

    assert result.ok is True
    assert result.strengths == ["구체적인 사례를 들었다"]
    assert result.improvements == ["문장이 너무 길다", "결론이 약하다"]
    assert result.revised_example == "다시 쓴 문단입니다"


def test_critique_unparseable_response_is_fail_open(monkeypatch):
    monkeypatch.setattr(self_introduction.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "이건 JSON이 아닙니다."

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = critique(content="자기소개서 본문")

    assert result.ok is False
    assert result.message == self_introduction._PARSE_FAIL_MESSAGE
