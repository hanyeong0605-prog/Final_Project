"""resume/project.py 단위 테스트 - test_self_introduction.py와 같은 패턴, JSON 파싱까지
직접 검증한다는 점만 다르다(project는 generate도 4개 필드짜리 JSON을 받는다)."""

from unittest.mock import patch

from app.domain.resume import project
from app.domain.resume.project import (
    GUIDED_QUESTIONS,
    ProjectCritique,
    ProjectDraft,
    critique,
    generate_draft,
)


def test_no_api_key_returns_guidance_message_for_generate(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "")

    draft = generate_draft(title="쇼핑몰 프로젝트", answers=["백엔드 담당"])

    assert isinstance(draft, ProjectDraft)
    assert draft.ok is False
    assert draft.message == project._NO_KEY_MESSAGE


def test_no_answers_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        draft = generate_draft(answers=["", "  "])

    assert draft.ok is False
    assert draft.message == project._NO_ANSWER_MESSAGE
    mock_client.assert_not_called()


def test_generate_draft_parses_four_fields(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = (
            '{"role_description": "백엔드 API 설계", "problem_description": "N+1 쿼리 발생", '
            '"solution_description": "fetch join 적용", "result_description": "응답 속도 3배 개선"}'
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            assert GUIDED_QUESTIONS[0] in contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        draft = generate_draft(
            title="쇼핑몰 프로젝트", job="백엔드 개발자", answers=["백엔드를 맡았습니다", "쿼리가 느렸습니다", "인덱스를 걸었습니다", "3배 빨라졌습니다"]
        )

    assert draft.ok is True
    assert draft.role_description == "백엔드 API 설계"
    assert draft.problem_description == "N+1 쿼리 발생"
    assert draft.solution_description == "fetch join 적용"
    assert draft.result_description == "응답 속도 3배 개선"


def test_generate_draft_unparseable_response_is_fail_open(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "JSON이 아닌 응답"

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        draft = generate_draft(answers=["답변"])

    assert draft.ok is False
    assert draft.message == project._PARSE_FAIL_MESSAGE


def test_generate_draft_api_failure_is_fail_open(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client", side_effect=RuntimeError("network down")):
        draft = generate_draft(answers=["답변"])

    assert draft.ok is False


def test_no_api_key_returns_guidance_message_for_critique(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "")

    result = critique(role_description="백엔드 담당")

    assert isinstance(result, ProjectCritique)
    assert result.ok is False
    assert result.message == project._NO_KEY_MESSAGE


def test_all_fields_empty_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = critique()

    assert result.ok is False
    assert result.message == project._NO_CONTENT_MESSAGE
    mock_client.assert_not_called()


def test_critique_parses_structured_response(monkeypatch):
    monkeypatch.setattr(project.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = (
            '{"strengths": ["기술 선택 이유가 명확함"], '
            '"improvements": ["정량적 성과가 없음"], '
            '"revised_example": "응답 속도를 1.2초에서 0.4초로 개선했습니다."}'
        )

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = critique(role_description="백엔드 담당", result_description="빨라졌습니다")

    assert result.ok is True
    assert result.strengths == ["기술 선택 이유가 명확함"]
    assert result.improvements == ["정량적 성과가 없음"]
    assert result.revised_example == "응답 속도를 1.2초에서 0.4초로 개선했습니다."
