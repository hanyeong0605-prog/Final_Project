"""POST /assistant/chat 라우팅 테스트 - chat.py의 실제 로직은 test_assistant_chat.py가
담당하고, 여기선 요청/응답 배선(history 파싱, chat() 위임)만 확인한다."""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.assistant import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def test_chat_endpoint_delegates_to_chat_module(monkeypatch):
    captured = {}

    def fake_chat(message, history):
        captured["message"] = message
        captured["history"] = history
        return type("R", (), {"to_dict": lambda self: {"ok": True, "message": None, "reply": "안녕하세요!", "navigate_to": None}})()

    monkeypatch.setattr(router_module, "run_chat", fake_chat)

    client = _make_client()
    res = client.post(
        "/chat",
        json={"message": "안녕", "history": [{"role": "user", "content": "이전 메시지"}]},
    )

    assert res.status_code == 200
    assert res.json() == {"ok": True, "message": None, "reply": "안녕하세요!", "navigate_to": None}
    assert captured["message"] == "안녕"
    assert captured["history"] == [{"role": "user", "content": "이전 메시지"}]


def test_chat_endpoint_defaults_history_to_empty_list():
    client = _make_client()
    # gemini_api_key가 없는 테스트 환경이면 chat()이 바로 fail-open으로 응답한다 - 여기선
    # 요청이 400/422 없이 정상적으로 라우팅되는지만 본다.
    res = client.post("/chat", json={"message": "안녕"})

    assert res.status_code == 200
    assert "ok" in res.json()
