"""assistant/knowledge.py 단위 테스트 - 사이트 챗봇 RAG 지식 검색.

question_similarity.py 테스트와 같은 이유로 실제 지식 파일(data/site_knowledge.jsonl,
목킹하지 않음)을 그대로 써서 검증한다 - TF-IDF는 항목 수가 적으면 IDF 통계가 제대로
안 잡혀서, 손으로 지어낸 2~3개짜리 가짜 지식으로는 실제 운영 규모(수십 개)의 동작을
검증할 수 없다."""

from app.domain.assistant import knowledge


def test_relevant_query_returns_matching_topic():
    results = knowledge.search("구독 요금이 얼마야")
    assert results
    assert results[0]["topic"] == "구독 요금"
    assert results[0]["score"] >= knowledge.SIMILARITY_THRESHOLD


def test_unrelated_query_returns_empty():
    results = knowledge.search("오늘 점심 뭐 먹지")
    assert results == []


def test_empty_query_returns_empty():
    assert knowledge.search("") == []
    assert knowledge.search("   ") == []


def test_results_are_sorted_by_score_descending():
    results = knowledge.search("모의면접 질문 몇 개까지 풀 수 있어")
    scores = [r["score"] for r in results]
    assert scores == sorted(scores, reverse=True)


def test_top_k_limits_result_count():
    results = knowledge.search("모의면접", top_k=1)
    assert len(results) <= 1


def test_knowledge_prompt_block_formats_as_bullet_list():
    block = knowledge.knowledge_prompt_block("구독 요금이 얼마야")
    assert block.startswith("- 구독 요금:")


def test_knowledge_prompt_block_empty_for_unrelated_query():
    assert knowledge.knowledge_prompt_block("오늘 점심 뭐 먹지") == ""


def test_missing_knowledge_file_returns_empty(monkeypatch, tmp_path):
    """지식 파일이 없는 극단적인 경우에도 예외 없이 빈 결과를 돌려줘야 한다. _cache와
    _KNOWLEDGE_PATH 둘 다 monkeypatch로 바꿔서, 테스트가 끝나면 원래 값(다른 테스트가
    이미 채워놓은 실제 캐시 포함)으로 자동 복원되게 한다 - question_corpus.py 테스트에서
    reload_pools() 직접 호출 대신 monkeypatch.setattr을 쓰기로 한 것과 같은 이유다."""
    monkeypatch.setattr(knowledge, "_cache", None)
    monkeypatch.setattr(knowledge, "_KNOWLEDGE_PATH", tmp_path / "없는파일.jsonl")

    assert knowledge.search("구독 요금") == []
