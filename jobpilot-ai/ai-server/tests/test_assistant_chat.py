"""assistant/chat.py 단위 테스트.

self_introduction.py 테스트와 같은 패턴 - Gemini는 목킹하고, fail-open 가드 + navigate_to
검증(목록에 없는 경로는 반드시 null로 떨어지는지)에 집중한다.
"""

from unittest.mock import patch

from app.domain.assistant import chat as chat_module
from app.domain.assistant.chat import AssistantReply, chat


def test_no_api_key_returns_guidance_message(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "")

    result = chat(message="안녕")

    assert isinstance(result, AssistantReply)
    assert result.ok is False
    assert result.message == chat_module._NO_KEY_MESSAGE


def test_empty_message_returns_guidance_without_calling_api(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client") as mock_client:
        result = chat(message="   ")

    assert result.ok is False
    assert result.message == chat_module._NO_MESSAGE_MESSAGE
    mock_client.assert_not_called()


def test_chat_returns_reply_and_known_navigate_to(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = '{"reply": "이력서 작성 도우미로 안내해드릴게요!", "navigate_to": "/resume"}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            assert "/resume" in contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = chat(message="이력서 쓰고 싶어")

    assert result.ok is True
    assert result.reply == "이력서 작성 도우미로 안내해드릴게요!"
    assert result.navigate_to == "/resume"


def test_unknown_navigate_to_is_nulled_out(monkeypatch):
    """Gemini가 목록에 없는 경로를 지어내도 절대 그대로 내보내면 안 된다 - 프론트가 그걸
    믿고 useNavigate()로 라우팅하면 404가 뜬다."""
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = '{"reply": "안내해드릴게요!", "navigate_to": "/does-not-exist"}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = chat(message="아무데나 가줘")

    assert result.ok is True
    assert result.navigate_to is None


def test_no_navigation_intent_leaves_navigate_to_null(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = '{"reply": "면접 볼 때는 두괄식으로 답하는 게 좋아요.", "navigate_to": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = chat(message="면접 팁 알려줘")

    assert result.ok is True
    assert result.navigate_to is None


def test_history_included_in_prompt(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = '{"reply": "네, 이어서 도와드릴게요.", "navigate_to": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    history = [
        {"role": "user", "content": "채용공고 어디서 봐?"},
        {"role": "assistant", "content": "전체 채용공고 페이지에서 볼 수 있어요."},
    ]

    with patch("google.genai.Client", FakeClient):
        chat(message="맞춤 추천도 있어?", history=history)

    assert "채용공고 어디서 봐?" in captured["prompt"]
    assert "전체 채용공고 페이지에서 볼 수 있어요." in captured["prompt"]


def test_unparseable_response_is_fail_open(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "JSON이 아닌 응답입니다."

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = chat(message="안녕")

    assert result.ok is False
    assert result.message == chat_module._PARSE_FAIL_MESSAGE


def test_api_failure_is_fail_open(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    with patch("google.genai.Client", side_effect=RuntimeError("network down")):
        result = chat(message="안녕")

    assert result.ok is False
    assert result.reply is None


def test_site_knowledge_included_when_relevant(monkeypatch):
    """2026-08-20 RAG 추가: 사이트 고유 정책과 관련 있는 질문이면 knowledge.py가 찾아준
    지식 조각이 프롬프트에 들어가야 한다."""
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = '{"reply": "월 9,900원이에요.", "navigate_to": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        chat(message="구독 요금이 얼마야")

    # 작성 규칙 4번에도 "[사이트 지식 참고자료]"라는 문구가 고정으로 들어가므로, 실제
    # 주입된 섹션(그 뒤에 " - 사용자 질문과..." 설명이 붙는 헤더)인지로 구분해서 확인한다.
    assert "[사이트 지식 참고자료 - 사용자 질문과" in captured["prompt"]
    assert "9,900원" in captured["prompt"]


def test_site_knowledge_section_omitted_when_no_match(monkeypatch):
    """관련 지식이 없는 일반 잡담이면 [사이트 지식 참고자료] 섹션 자체가 프롬프트에
    안 들어가야 한다 - 억지로 무관한 내용을 근거인 척 끼워넣으면 안 된다."""
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")

    captured = {}

    class FakeResponse:
        text = '{"reply": "저도 잘 모르겠어요!", "navigate_to": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        chat(message="오늘 점심 뭐 먹지")

    assert "[사이트 지식 참고자료 - 사용자 질문과" not in captured["prompt"]


def test_active_personal_matches_are_included_only_for_job_question(monkeypatch):
    monkeypatch.setattr(chat_module.settings, "gemini_api_key", "fake-key")
    captured = {}

    class FakeResponse:
        text = '{"reply": "추천 공고를 안내해드릴게요.", "navigate_to": null}'

    class FakeModels:
        def generate_content(self, model, contents, config=None):
            captured["prompt"] = contents
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None): self.models = FakeModels()

    reference = chat_module.JobMatchReference(
        job_posting_id=12, company_name="잡드림", title="백엔드 개발자",
        source_url="https://example.test/jobs/12", readiness_score=90,
        recommendation_level="APPLY_NOW",
    )
    monkeypatch.setattr(chat_module, "fetch_active_matches", lambda member_id: [reference])

    with patch("google.genai.Client", FakeClient):
        result = chat(message="내게 맞는 채용공고 추천해줘", member_id=7)

    assert "[현재 회원의 모집 중 매칭 공고" in captured["prompt"]
    assert result.job_references == [reference.to_dict()]
