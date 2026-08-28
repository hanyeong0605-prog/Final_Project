"""next_question() 라우팅 동작 테스트.

2026-08-06 수정: 원래 있던 NextQuestionRequest.wants_personalized()가 없어지고, 이제
/next-question은 정보 유무와 상관없이 항상 Gemini(generate_personalized_question)를 먼저
시도하고, 그게 None을 반환할 때만 LoRA 경로로 폴백한다 - "면접 분야 선택 안 함" + 프로필
미입력 조합에서 저품질 LoRA 질문이 나오던 문제를 고치면서 생긴 변경(question_generator.py의
generate_personalized_question 설계 메모 참고).

2026-08-07 수정: LoRA 폴백이 generate_question(원본 생성만) 대신 generate_validated_question
(생성 + 임베딩 유사도 검증 + 코퍼스 대체)을 호출하도록 바뀌었다(question_generator.py의
generate_validated_question 설계 메모 참고) - 아래 테스트들도 그에 맞춰 monkeypatch 대상을
바꿨다. LoRA 모델 로딩은 GPU/모델 파일이 필요해 여기서 실제로 호출하지 않고, router 모듈에
import된 함수들을 monkeypatch로 대체해서 "어느 경로가 실제로 호출됐는지"만 검증한다.

2026-08-29 수정: mode(practice/real)가 생기면서 기본값이 practice(코퍼스)로 바뀌었다 -
Gemini/LoRA 경로를 검증하던 기존 테스트들은 mode="real"을 명시해서 그대로 유지한다.
새로 추가된 것은 (1) 무료 요청이 Gemini 경로에 도달조차 못 하는지, (2) source별로 어떤 RAG
문맥이 조립돼 넘어가는지, (3) 필수 ID가 빠졌을 때 400인지다. RAG 조회 함수들은 전부 DB를
타므로 router 모듈에 import된 이름을 monkeypatch로 대체한다.
"""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.domain.interview import router as router_module


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router_module.router)
    return TestClient(app)


def _forbid_gemini(monkeypatch):
    """Gemini 경로에 발이라도 닿으면 즉시 실패시킨다."""

    def _fail(**kw):
        raise AssertionError("무료(practice) 요청은 Gemini를 호출하면 안 된다")

    monkeypatch.setattr(router_module, "generate_personalized_question", _fail)


def _capture_personalized(monkeypatch, question: str = "생성된 질문입니다."):
    """generate_personalized_question에 실제로 넘어간 인자를 붙잡아 둔다."""
    captured: dict = {}

    def fake(**kwargs):
        captured.update(kwargs)
        return question

    monkeypatch.setattr(router_module, "generate_personalized_question", fake)
    return captured


def _forbid_rag(monkeypatch, *names: str):
    for name in names:
        def _fail(*args, _name=name, **kwargs):
            raise AssertionError(f"{_name}이(가) 호출되면 안 된다")

        monkeypatch.setattr(router_module, name, _fail)


# --------------------------------------------------------------------------------------
# 무료 모의면접(practice)
# --------------------------------------------------------------------------------------


def test_practice_mode_uses_corpus_only(monkeypatch):
    monkeypatch.setattr(router_module.question_corpus, "pick_question", lambda *args: "코퍼스 질문")
    _forbid_gemini(monkeypatch)

    res = _make_client().post("/next-question", json={"mode": "practice", "category": "기술_직무역량"})

    assert res.status_code == 200
    assert res.json() == {"question": "코퍼스 질문"}


def test_practice_mode_is_the_default(monkeypatch):
    """mode를 안 보낸 요청은 무료로 취급한다 - 필드가 빠졌다고 Gemini 비용이 나가면 안 된다."""
    monkeypatch.setattr(router_module.question_corpus, "pick_question", lambda *args: "코퍼스 질문")
    _forbid_gemini(monkeypatch)

    res = _make_client().post("/next-question", json={})

    assert res.status_code == 200
    assert res.json() == {"question": "코퍼스 질문"}


def test_practice_mode_ignores_rag_ids(monkeypatch):
    """무료 요청에 공고/회원 ID가 섞여 와도 RAG 조회를 하지 않는다 - 무료 경로가 DB나 Gemini
    비용을 유발할 수 있는 코드에 아예 도달하지 못하는 구조인지 확인한다."""
    monkeypatch.setattr(router_module.question_corpus, "pick_question", lambda *args: "코퍼스 질문")
    _forbid_gemini(monkeypatch)
    _forbid_rag(monkeypatch, "fetch_member_spec", "build_member_spec_context", "build_job_requirements_context")

    res = _make_client().post(
        "/next-question",
        json={"mode": "practice", "member_id": 7, "job_posting_id": 33, "source": "spec_company"},
    )

    assert res.status_code == 200
    assert res.json() == {"question": "코퍼스 질문"}


def test_legacy_corpus_only_flag_still_forces_corpus(monkeypatch):
    """mode를 모르는 예전 클라이언트(corpus_only=true만 보냄)도 그대로 무료로 동작해야 한다."""
    monkeypatch.setattr(router_module.question_corpus, "pick_question", lambda *args: "코퍼스 질문")
    _forbid_gemini(monkeypatch)

    res = _make_client().post("/next-question", json={"mode": "real", "corpus_only": True})

    assert res.status_code == 200
    assert res.json() == {"question": "코퍼스 질문"}


def test_empty_corpus_returns_503(monkeypatch):
    monkeypatch.setattr(router_module.question_corpus, "pick_question", lambda *args: None)

    res = _make_client().post("/next-question", json={"mode": "practice"})

    assert res.status_code == 503


# --------------------------------------------------------------------------------------
# 실전면접(real) - 근거(source)별 RAG 문맥
# --------------------------------------------------------------------------------------


def test_real_spec_source_passes_member_context_only(monkeypatch):
    captured = _capture_personalized(monkeypatch)
    monkeypatch.setattr(router_module, "fetch_member_spec", lambda member_id: {"stub": member_id})
    monkeypatch.setattr(
        router_module, "build_member_spec_context", lambda member_id, category, spec=None: "회원 스펙 블록"
    )
    _forbid_rag(monkeypatch, "build_job_requirements_context")

    res = _make_client().post(
        "/next-question", json={"mode": "real", "source": "spec", "member_id": 7, "category": "기술_직무역량"}
    )

    assert res.status_code == 200
    assert captured["member_spec_context"] == "회원 스펙 블록"
    assert captured["job_requirements_context"] == ""
    assert captured["gap_context"] == ""
    assert captured["interview_mode"] == "real"


def test_real_company_source_passes_requirements_only(monkeypatch):
    captured = _capture_personalized(monkeypatch)
    monkeypatch.setattr(
        router_module, "build_job_requirements_context", lambda job_posting_id, category: "공고 요구사항 블록"
    )
    _forbid_rag(monkeypatch, "fetch_member_spec", "build_member_spec_context")

    res = _make_client().post(
        "/next-question", json={"mode": "real", "source": "company", "job_posting_id": 33}
    )

    assert res.status_code == 200
    assert captured["job_requirements_context"] == "공고 요구사항 블록"
    assert captured["member_spec_context"] == ""
    assert captured["gap_context"] == ""


def test_real_spec_company_source_passes_all_three_contexts(monkeypatch):
    captured = _capture_personalized(monkeypatch)
    monkeypatch.setattr(router_module, "fetch_member_spec", lambda member_id: {"stub": member_id})
    monkeypatch.setattr(
        router_module, "build_member_spec_context", lambda member_id, category, spec=None: "회원 스펙 블록"
    )
    monkeypatch.setattr(
        router_module, "build_job_requirements_context", lambda job_posting_id, category: "공고 요구사항 블록"
    )
    monkeypatch.setattr(router_module, "fetch_narrowed_requirements", lambda job_posting_id, category: ["요구사항"])
    monkeypatch.setattr(router_module, "build_gap_context", lambda spec, requirements: "대조 블록")

    res = _make_client().post(
        "/next-question",
        json={"mode": "real", "source": "spec_company", "member_id": 7, "job_posting_id": 33},
    )

    assert res.status_code == 200
    assert captured["member_spec_context"] == "회원 스펙 블록"
    assert captured["job_requirements_context"] == "공고 요구사항 블록"
    assert captured["gap_context"] == "대조 블록"


def test_real_spec_is_fetched_once_and_reused_for_gap(monkeypatch):
    """스펙 조회는 한 요청에 한 번만 - 문맥 조립과 대조가 같은 결과를 나눠 쓴다."""
    _capture_personalized(monkeypatch)
    fetched: list[int] = []
    seen: dict = {}

    def fake_fetch(member_id):
        fetched.append(member_id)
        return "스펙"

    def fake_member_context(member_id, category, spec=None):
        seen["context_spec"] = spec
        return ""

    def fake_gap(spec, requirements):
        seen["gap_spec"] = spec
        return ""

    monkeypatch.setattr(router_module, "fetch_member_spec", fake_fetch)
    monkeypatch.setattr(router_module, "build_member_spec_context", fake_member_context)
    monkeypatch.setattr(router_module, "build_job_requirements_context", lambda *a: "")
    monkeypatch.setattr(router_module, "fetch_narrowed_requirements", lambda *a: ["요구사항"])
    monkeypatch.setattr(router_module, "build_gap_context", fake_gap)

    res = _make_client().post(
        "/next-question",
        json={"mode": "real", "source": "spec_company", "member_id": 7, "job_posting_id": 33},
    )

    assert res.status_code == 200
    assert fetched == [7]
    assert seen["context_spec"] == "스펙"
    assert seen["gap_spec"] == "스펙"


@pytest.mark.parametrize(
    ("payload", "missing"),
    [
        ({"source": "spec"}, "member_id"),
        ({"source": "company"}, "job_posting_id"),
        ({"source": "spec_company", "job_posting_id": 33}, "member_id"),
        ({"source": "spec_company", "member_id": 7}, "job_posting_id"),
    ],
)
def test_real_missing_required_id_returns_400(monkeypatch, payload, missing):
    """근거에 필요한 ID가 없으면 조용히 빈 문맥으로 넘어가지 않고 400으로 끊는다 - RAG 조회가
    전부 fail-open이라 이걸 안 막으면 "스펙이 반영 안 된 실전면접"이 원인 없이 나온다."""
    _forbid_gemini(monkeypatch)

    res = _make_client().post("/next-question", json={"mode": "real", **payload})

    assert res.status_code == 400
    assert missing in res.json()["detail"]


def test_real_without_source_keeps_legacy_job_posting_behavior(monkeypatch):
    """source를 안 보내는 기존 흐름(공고만 고르고 시작)은 예전 그대로 동작해야 한다."""
    captured = _capture_personalized(monkeypatch)
    monkeypatch.setattr(
        router_module, "build_job_requirements_context", lambda job_posting_id, category: f"공고 {job_posting_id}"
    )
    _forbid_rag(monkeypatch, "fetch_member_spec", "build_member_spec_context")

    res = _make_client().post("/next-question", json={"mode": "real", "job_posting_id": 33})

    assert res.status_code == 200
    assert captured["job_requirements_context"] == "공고 33"


def test_real_passes_exclude_as_asked_questions(monkeypatch):
    """이미 나온 질문은 프롬프트의 중복 금지 규칙에도 쓰인다(사후 중복 판정과 이중 방어)."""
    captured = _capture_personalized(monkeypatch)
    monkeypatch.setattr(router_module, "build_job_requirements_context", lambda *a: "")

    res = _make_client().post(
        "/next-question", json={"mode": "real", "exclude": ["자기소개를 해주세요."]}
    )

    assert res.status_code == 200
    assert captured["asked_questions"] == ["자기소개를 해주세요."]


# --------------------------------------------------------------------------------------
# 실전면접(real) - 기존 Gemini/LoRA 폴백 (2026-08-06/08-07 도입분)
# --------------------------------------------------------------------------------------


def test_personalized_success_skips_lora(monkeypatch):
    """Gemini가 질문을 성공적으로 반환하면 LoRA 경로(generate_validated_question)는 아예
    호출되지 않아야 한다 - 불필요하게 무거운 모델을 로드하지 않는다는 뜻이기도 하다."""
    monkeypatch.setattr(
        router_module, "generate_personalized_question", lambda **kw: "Gemini가 만든 질문입니다."
    )

    def _fail_if_called(**kw):
        raise AssertionError("Gemini가 성공했는데 LoRA 경로가 호출되면 안 된다")

    monkeypatch.setattr(router_module, "generate_validated_question", _fail_if_called)

    res = _make_client().post("/next-question", json={"mode": "real"})
    assert res.status_code == 200
    assert res.json()["question"] == "Gemini가 만든 질문입니다."


def test_personalized_none_falls_back_to_lora(monkeypatch):
    """Gemini가 None(키 없음/호출 실패 등)을 반환하면 LoRA 경로(생성+검증)로 폴백해야 한다."""
    monkeypatch.setattr(router_module, "generate_personalized_question", lambda **kw: None)
    monkeypatch.setattr(
        router_module, "generate_validated_question", lambda **kw: "LoRA가 만든 질문입니다."
    )

    res = _make_client().post("/next-question", json={"mode": "real"})
    assert res.status_code == 200
    assert res.json()["question"] == "LoRA가 만든 질문입니다."


def test_personalized_always_attempted_even_without_info(monkeypatch):
    """job/tech_summary가 전혀 없는 요청(과거엔 이 경우 곧바로 LoRA로 갔음)이어도
    Gemini 경로를 먼저 시도해야 한다 - "선택 안 함" + 프로필 미입력 사용자도 이제
    LoRA보다 나은 Gemini 질문을 받아야 하기 때문에 생긴 요구사항이다."""
    captured = _capture_personalized(monkeypatch, "질문")

    res = _make_client().post("/next-question", json={"mode": "real"})
    assert res.status_code == 200
    assert "job" in captured
