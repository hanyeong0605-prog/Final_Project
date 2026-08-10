"""POST /timeline/insight/generate 라우팅(배선) 테스트."""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.timeline import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def test_insight_endpoint_delegates_to_generate_insight(monkeypatch):
    captured = {}

    def fake_generate(sessions, self_introductions, projects):
        captured["sessions"] = sessions
        captured["self_introductions"] = self_introductions
        captured["projects"] = projects
        return type("R", (), {"to_dict": lambda self: {"ok": True, "message": None, "recurring_points": [], "resume_linked_suggestion": None}})()

    monkeypatch.setattr(router_module, "generate_insight", fake_generate)

    client = _make_client()
    res = client.post(
        "/insight/generate",
        json={
            "sessions": [{"role": "BACKEND", "overall_score": 4, "improvements": ["A"]}],
            "self_introductions": ["글"],
            "projects": [],
        },
    )

    assert res.status_code == 200
    assert captured["sessions"] == [{"role": "BACKEND", "interview_type": "", "overall_score": 4, "improvements": ["A"]}]
    assert captured["self_introductions"] == ["글"]
