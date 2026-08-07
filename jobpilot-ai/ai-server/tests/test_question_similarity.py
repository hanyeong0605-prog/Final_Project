"""question_similarity.is_topically_relevant() / similarity_score() 단위 테스트.

Gemini Embedding API(client.models.embed_content)는 evaluation.py/question_generator.py의
기존 테스트들과 같은 패턴(google.genai.Client 모킹)으로 대체한다. 캐시 파일은 tmp_path로
격리해서 테스트끼리 서로 영향을 주지 않게 한다.
"""

from unittest.mock import patch

from app.domain.interview import question_corpus, question_similarity


class _FakeEmbedding:
    def __init__(self, values):
        self.values = values


class _FakeEmbedResult:
    def __init__(self, embeddings):
        self.embeddings = embeddings


def _make_fake_client(vectors_by_call_order):
    """호출될 때마다 vectors_by_call_order에서 하나씩 꺼내 쓰는 가짜 Client.
    contents가 리스트로 오면(배치) 그 개수만큼 벡터를 묶어서 돌려준다."""
    calls = {"count": 0}

    class FakeModels:
        def embed_content(self, model, contents):
            texts = contents if isinstance(contents, list) else [contents]
            vectors = vectors_by_call_order[calls["count"]]
            calls["count"] += 1
            assert len(vectors) == len(texts)
            return _FakeEmbedResult([_FakeEmbedding(v) for v in vectors])

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    return FakeClient


def _setup_pool(tmp_path, monkeypatch, pool):
    monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: pool)
    monkeypatch.setattr(question_similarity, "_CACHE_PATH", tmp_path / "cache.json")


def test_no_api_key_is_fail_open(monkeypatch):
    """키가 없으면 검증 자체를 건너뛰고 True(신뢰)를 돌려줘야 한다 - LoRA 폴백이 검증
    인프라 때문에 막히면 안 된다."""
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "")
    assert question_similarity.is_topically_relevant("아무 질문", "기술_직무역량", "백엔드") is True


def test_empty_pool_returns_none_score_and_is_relevant(tmp_path, monkeypatch):
    """비교할 코퍼스 풀이 없으면(그 category/job 조합 데이터가 아예 없음) 검증을 포기하고
    True를 돌려줘야 한다 - 기준이 없는데 억지로 떨어뜨리면 안 된다."""
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    _setup_pool(tmp_path, monkeypatch, [])

    assert question_similarity.similarity_score("아무 질문", "기술_직무역량", "백엔드") is None
    assert question_similarity.is_topically_relevant("아무 질문", "기술_직무역량", "백엔드") is True


def test_similar_question_passes_threshold(tmp_path, monkeypatch):
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    _setup_pool(tmp_path, monkeypatch, ["JPA N+1 문제를 설명해 주세요."])

    # 첫 호출(_get_pool_embeddings)은 풀 임베딩(1개), 두번째 호출은 후보 질문 임베딩.
    fake_client = _make_fake_client([[[1.0, 0.0]], [[0.99, 0.01]]])
    with patch("google.genai.Client", fake_client):
        score = question_similarity.similarity_score("JPA N+1 문제 해결 경험을 말씀해 주세요.", "기술_직무역량", "백엔드")
        assert score > question_similarity.SIMILARITY_THRESHOLD

    with patch("google.genai.Client", fake_client):
        assert question_similarity.is_topically_relevant(
            "JPA N+1 문제 해결 경험을 말씀해 주세요.", "기술_직무역량", "백엔드"
        ) is True


def test_dissimilar_question_fails_threshold(tmp_path, monkeypatch):
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    _setup_pool(tmp_path, monkeypatch, ["JPA N+1 문제를 설명해 주세요."])

    # 완전히 직교하는 벡터 -> 코사인 유사도 0.
    fake_client = _make_fake_client([[[1.0, 0.0]], [[0.0, 1.0]]])
    with patch("google.genai.Client", fake_client):
        assert question_similarity.is_topically_relevant(
            "GitHub와 Docker의 차이점을 설명해 주세요.", "기술_직무역량", "모바일 (iOS/Android)"
        ) is False


def test_embedding_call_failure_is_fail_open(tmp_path, monkeypatch):
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    _setup_pool(tmp_path, monkeypatch, ["JPA N+1 문제를 설명해 주세요."])

    class BrokenClient:
        def __init__(self, api_key=None):
            raise RuntimeError("network down")

    with patch("google.genai.Client", BrokenClient):
        assert question_similarity.is_topically_relevant("아무 질문", "기술_직무역량", "백엔드") is True


def test_pool_embeddings_are_cached_to_disk(tmp_path, monkeypatch):
    """같은 풀(해시 동일)로 두 번 호출하면 두 번째는 캐시를 써서 embed_content를 다시
    호출하지 않아야 한다."""
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    _setup_pool(tmp_path, monkeypatch, ["JPA N+1 문제를 설명해 주세요."])

    fake_client = _make_fake_client([[[1.0, 0.0]]])
    with patch("google.genai.Client", fake_client):
        pool1, emb1 = question_similarity._get_pool_embeddings("기술_직무역량", "백엔드")

    assert (tmp_path / "cache.json").exists()

    # 두 번째 호출 - fake_client를 다시 안 넘겨도(patch 없이) 캐시에서 바로 읽혀야 하므로
    # embed_content가 호출되면 여기서 인덱스 에러로 실패한다(vectors_by_call_order가 1개뿐).
    pool2, emb2 = question_similarity._get_pool_embeddings("기술_직무역량", "백엔드")

    assert pool1 == pool2 == ["JPA N+1 문제를 설명해 주세요."]
    assert emb1 == emb2 == [[1.0, 0.0]]
