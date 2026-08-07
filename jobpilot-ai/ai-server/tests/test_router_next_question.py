"""next_question() 라우팅 동작 테스트.

2026-08-06 수정: 원래 있던 NextQuestionRequest.wants_personalized()가 없어지고, 이제
/next-question은 정보 유무와 상관없이 항상 Gemini(generate_personalized_question)를 먼저
시도하고, 그게 None을 반환할 때만 LoRA(generate_question)로 폴백한다 - "면접 분야 선택
안 함" + 프로필 미입력 조합에서 저품질 LoRA 질문이 나오던 문제를 고치면서 생긴 변경
(question_generator.py의 generate_personalized_question 설계 메모 참고). LoRA 모델
로딩은 GPU/모델 파일이 필요해 여기서 실제로 호출하지 않고, router 모듈에 import된 두
함수를 monkeypatch로 대체해서 "어느 경로가 실제로 호출됐는지"만 검증한다.
"""

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.interview import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def test_personalized_success_skips_lora(monkeypatch):
    """Gemini가 질문을 성공적으로 반환하면 LoRA(generate_question)는 아예 호출되지
    않아야 한다 - 불필요하게 무거운 모델을 로드하지 않는다는 뜻이기도 하다."""
    monkeypatch.setattr(
        router_module, "generate_personalized_question", lambda **kw: "Gemini가 만든 질문입니다."
    )

    def _fail_if_called(**kw):
        raise AssertionError("Gemini가 성공했는데 LoRA 경로가 호출되면 안 된다")

    monkeypatch.setattr(router_module, "generate_question", _fail_if_called)

    client = _make_client()
    res = client.post("/next-question", json={})
    assert res.status_code == 200
    assert res.json()["question"] == "Gemini가 만든 질문입니다."


def test_personalized_none_falls_back_to_lora(monkeypatch):
    """Gemini가 None(키 없음/호출 실패 등)을 반환하면 LoRA 경로로 폴백해야 한다."""
    monkeypatch.setattr(router_module, "generate_personalized_question", lambda **kw: None)
    monkeypatch.setattr(
        router_module, "generate_question", lambda **kw: "LoRA가 만든 질문입니다."
    )

    client = _make_client()
    res = client.post("/next-question", json={})
    assert res.status_code == 200
    assert res.json()["question"] == "LoRA가 만든 질문입니다."


def test_personalized_always_attempted_even_without_info(monkeypatch):
    """job/tech_summary가 전혀 없는 요청(과거엔 이 경우 곧바로 LoRA로 갔음)이어도
    Gemini 경로를 먼저 시도해야 한다 - "선택 안 함" + 프로필 미입력 사용자도 이제
    LoRA보다 나은 Gemini 질문을 받아야 하기 때문에 생긴 요구사항이다."""
    captured = {}

    def fake_personalized(job, tech_summary, category, angle_hint=""):
        captured["called"] = True
        captured["job"] = job
        captured["tech_summary"] = tech_summary
        return "질문"

    monkeypatch.setattr(router_module, "generate_personalized_question", fake_personalized)

    client = _make_client()
    res = client.post("/next-question", json={})
    assert res.status_code == 200
    assert captured.get("called") is True
