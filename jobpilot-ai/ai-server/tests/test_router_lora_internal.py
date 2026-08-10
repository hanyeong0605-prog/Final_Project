"""POST /internal/lora/generate-candidates 라우팅 테스트.

2026-08-10 추가: 이 엔드포인트는 Tailscale로 연결된 로컬/학원 PC(모델 파일이 실제로 있는
쪽)에서 이 ai-server를 띄웠을 때, EC2가 원격으로 후보 생성을 요청하는 용도다
(question_generator.py _fetch_raw_candidates_remote 참고). 실제 torch 추론은 여기서 다루지
않고(_generate_raw_candidates_locally를 monkeypatch), 인증(X-Internal-Key)과 실패 시
상태코드만 검증한다 - 같은 패턴을 test_router_next_question.py에서 가져왔다.
"""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.interview import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def test_no_key_configured_allows_request(monkeypatch):
    """lora_server_key를 아예 안 정한 로컬 개발 환경에서는 헤더가 없어도 통과해야 한다."""
    monkeypatch.setattr(router_module.settings, "lora_server_key", "")
    monkeypatch.setattr(
        router_module, "_generate_raw_candidates_locally", lambda **kw: ["질문 후보 1", "질문 후보 2"]
    )

    client = _make_client()
    res = client.post("/internal/lora/generate-candidates", json={"job": "백엔드"})

    assert res.status_code == 200
    assert res.json()["candidates"] == ["질문 후보 1", "질문 후보 2"]


def test_wrong_key_returns_401(monkeypatch):
    """lora_server_key가 설정돼 있는데 헤더값이 다르거나 없으면 401이어야 한다."""
    monkeypatch.setattr(router_module.settings, "lora_server_key", "correct-key")

    client = _make_client()
    res = client.post("/internal/lora/generate-candidates", json={"job": "백엔드"})

    assert res.status_code == 401


def test_correct_key_returns_candidates(monkeypatch):
    """올바른 X-Internal-Key 헤더를 실으면 통과하고, 로컬 추론 결과를 그대로 돌려줘야 한다."""
    monkeypatch.setattr(router_module.settings, "lora_server_key", "correct-key")
    monkeypatch.setattr(router_module, "_generate_raw_candidates_locally", lambda **kw: ["실제 LoRA 질문"])

    client = _make_client()
    res = client.post(
        "/internal/lora/generate-candidates",
        json={"job": "백엔드", "category": "기술_직무역량"},
        headers={"X-Internal-Key": "correct-key"},
    )

    assert res.status_code == 200
    assert res.json()["candidates"] == ["실제 LoRA 질문"]


def test_local_model_missing_returns_503(monkeypatch):
    """이 PC에도 모델 파일이 없으면(설정 실수 등) 503으로 명확히 알려줘야 한다 - 호출부
    (EC2)는 어차피 fail-open으로 처리하므로 그대로 로컬/코퍼스 폴백으로 넘어간다."""
    monkeypatch.setattr(router_module.settings, "lora_server_key", "")

    def _broken(**kw):
        raise RuntimeError("질문 생성 모델을 찾을 수 없습니다")

    monkeypatch.setattr(router_module, "_generate_raw_candidates_locally", _broken)

    client = _make_client()
    res = client.post("/internal/lora/generate-candidates", json={"job": "백엔드"})

    assert res.status_code == 503
