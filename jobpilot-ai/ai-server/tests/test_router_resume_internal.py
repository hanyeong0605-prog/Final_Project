"""POST /resume/technical-summary/synthesize 라우팅(인증) 테스트.

test_router_lora_internal.py와 같은 패턴 - 실제 Gemini 합성 로직(technical_summary.py)은
별도로 테스트하고, 여기선 X-Internal-Api-Key 검증과 정상 경로 위임만 확인한다. 다른
resume 엔드포인트(self-introduction/*, project/*)는 프론트가 직접 부르는 공개
엔드포인트라 이 검증이 없다 - 이 엔드포인트만 Spring이 서버 간 호출로 부르는 내부용이라
다르다(router.py의 _verify_internal_api_key 주석 참고).
"""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.resume import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def test_no_key_configured_returns_401(monkeypatch):
    """internal_api_key를 아예 안 정한 상태(로컬 개발이라도)에서는 요청을 거부해야
    한다 - 크롤러 내부 엔드포인트와 동일한 보수적 기본값."""
    monkeypatch.setattr(router_module.settings, "internal_api_key", "")

    client = _make_client()
    res = client.post("/technical-summary/synthesize", json={"job": "백엔드", "self_introductions": ["글"]})

    assert res.status_code == 401


def test_wrong_key_returns_401(monkeypatch):
    monkeypatch.setattr(router_module.settings, "internal_api_key", "correct-key")

    client = _make_client()
    res = client.post("/technical-summary/synthesize", json={"job": "백엔드", "self_introductions": ["글"]})

    assert res.status_code == 401


def test_correct_key_delegates_to_synthesize(monkeypatch):
    monkeypatch.setattr(router_module.settings, "internal_api_key", "correct-key")
    monkeypatch.setattr(
        router_module.technical_summary_module,
        "synthesize",
        lambda **kw: type("R", (), {"to_dict": lambda self: {"ok": True, "message": None, "summary": "합성된 요약"}})(),
    )

    client = _make_client()
    res = client.post(
        "/technical-summary/synthesize",
        json={"job": "백엔드", "self_introductions": ["글"]},
        headers={"X-Internal-Api-Key": "correct-key"},
    )

    assert res.status_code == 200
    assert res.json() == {"ok": True, "message": None, "summary": "합성된 요약"}
