from datetime import datetime

from app.domain.assistant import job_match_retrieval as retrieval
from app.domain.assistant.job_match_retrieval import JobMatchReference


def test_job_intent_detection_is_conservative():
    assert retrieval.is_job_question("내게 맞는 채용공고 추천해줘") is True
    assert retrieval.is_job_question("이 회사에 지원해도 될까?") is True
    assert retrieval.is_job_question("면접에서 긴장하지 않는 법 알려줘") is False


def test_fetch_active_matches_uses_authenticated_member_and_caps_limit(monkeypatch):
    captured = {}

    class Result:
        def mappings(self):
            return [{
                "job_posting_id": 31, "company_name": "잡드림", "title": "백엔드 개발자",
                "source_url": "https://example.test/jobs/31", "location": "서울",
                "deadline_at": datetime(2026, 9, 30), "readiness_score": 87.5,
                "recommendation_level": "APPLY_NOW", "summary_comment": "Spring 경험이 요구사항과 맞습니다.",
                "missing_required_count": 0,
            }]

    class Connection:
        def execute(self, statement, params):
            captured["sql"] = str(statement)
            captured["params"] = params
            return Result()
        def __enter__(self): return self
        def __exit__(self, *args): return None

    class Engine:
        def connect(self): return Connection()

    monkeypatch.setattr(retrieval, "get_engine", lambda: Engine())
    matches = retrieval.fetch_active_matches(7, limit=99)

    assert len(matches) == 1
    assert matches[0].job_posting_id == 31
    assert captured["params"] == {"member_id": 7, "limit": retrieval._MAX_MATCHES}
    assert "jm.member_id = :member_id" in captured["sql"]
    assert "jp.status = 'ACTIVE'" in captured["sql"]
    assert "jp.deadline_at >= NOW()" in captured["sql"]


def test_fetch_active_matches_fails_open_on_database_error(monkeypatch):
    monkeypatch.setattr(retrieval, "get_engine", lambda: (_ for _ in ()).throw(RuntimeError("db down")))
    assert retrieval.fetch_active_matches(7) == []


def test_prompt_block_contains_only_structured_match_facts():
    block = retrieval.job_matches_prompt_block([JobMatchReference(
        job_posting_id=31, company_name="잡드림", title="백엔드 개발자",
        source_url="https://example.test/jobs/31", location="서울",
        deadline_at=datetime(2026, 9, 30), readiness_score=87.5,
        recommendation_level="APPLY_NOW", summary_comment="Spring 경험이 요구사항과 맞습니다.",
        missing_required_count=0,
    )])

    assert "공고 ID 31" in block
    assert "적합도 88점" in block
    assert "미확인 필수요건 0개" in block
