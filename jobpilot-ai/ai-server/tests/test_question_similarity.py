"""question_similarity.is_topically_relevant() / similarity_score() 단위 테스트.

Gemini Embedding API(client.models.embed_content)는 evaluation.py/question_generator.py의
기존 테스트들과 같은 패턴(google.genai.Client 모킹)으로 대체한다. 캐시 파일은 tmp_path로
격리해서 테스트끼리 서로 영향을 주지 않게 한다.
"""

import json
from unittest.mock import patch

import pytest

from app.domain.interview import question_corpus, question_similarity


@pytest.fixture(autouse=True)
def _reset_cooldown():
    """2026-08-20 쿨다운 도입 후 추가: _cooldown_until은 모듈 전역 상태라 한 테스트에서
    예외 경로(fail-open)를 타면 다음 테스트까지 쿨다운이 새어나간다(monotonic 시계라
    테스트 사이에도 유지됨). 각 테스트 전후로 리셋해서 테스트 간 격리를 보장한다."""
    question_similarity._cooldown_until = 0.0
    yield
    question_similarity._cooldown_until = 0.0


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


def test_partial_pool_progress_is_saved_and_resumed_after_failure(tmp_path, monkeypatch):
    """2026-08-20 수정 검증: 풀 임베딩 도중 실패해도 그때까지의 배치는 캐시에 남아야 하고,
    다음 호출은 처음부터가 아니라 그 지점부터 이어서 임베딩해야 한다 - 8/13 할당량 소진의
    직접 원인이었던 '중간 실패 시 전체 진행분 소실'을 재발 방지한다."""
    monkeypatch.setattr(question_similarity, "_EMBED_BATCH_SIZE", 2)
    pool = [f"질문{i}" for i in range(5)]
    _setup_pool(tmp_path, monkeypatch, pool)

    # _embed_texts가 배치(chunk)마다 genai.Client(...)를 새로 만들기 때문에, 호출 횟수는
    # 클라이언트 인스턴스가 아니라 이 바깥 딕셔너리에 누적해야 한다.
    calls = {"count": 0}

    class FlakyModels:
        def embed_content(self, model, contents):
            calls["count"] += 1
            if calls["count"] == 1:
                return _FakeEmbedResult([_FakeEmbedding([1.0, 0.0]) for _ in contents])
            raise RuntimeError("rate limited")

    class FlakyClient:
        def __init__(self, api_key=None):
            self.models = FlakyModels()

    with patch("google.genai.Client", FlakyClient):
        with pytest.raises(RuntimeError):
            question_similarity._get_pool_embeddings("기술_직무역량", "백엔드")

    cache = json.loads((tmp_path / "cache.json").read_text(encoding="utf-8"))
    saved = cache["기술_직무역량|백엔드"]["embeddings"]
    assert len(saved) == 2  # 첫 배치(2개)까지는 저장되고 두번째 배치에서 실패

    class ResumeModels:
        def embed_content(self, model, contents):
            return _FakeEmbedResult([_FakeEmbedding([0.0, 1.0]) for _ in contents])

    class ResumeClient:
        def __init__(self, api_key=None):
            self.models = ResumeModels()

    with patch("google.genai.Client", ResumeClient):
        pool2, emb2 = question_similarity._get_pool_embeddings("기술_직무역량", "백엔드")

    assert pool2 == pool
    assert len(emb2) == 5
    assert emb2[:2] == [[1.0, 0.0], [1.0, 0.0]]  # 이전에 저장된 진행분은 재요청되지 않음
    assert emb2[2:] == [[0.0, 1.0]] * 3  # 나머지만 이어서 임베딩됨


def test_failure_starts_cooldown_and_skips_subsequent_validation(monkeypatch):
    """예외 발생 시 쿨다운이 걸리고, 쿨다운 중에는 embed_content를 아예 호출하지 않고
    fail-open으로 즉시 통과시켜야 한다 - 여러 요청이 동시에 재시도를 폭주시키는 것을 막는다."""
    monkeypatch.setattr(question_similarity.settings, "gemini_api_key", "fake-key")
    monkeypatch.setattr(question_similarity, "_cooldown_until", 0.0)

    def _boom(*args, **kwargs):
        raise RuntimeError("rate limited")

    monkeypatch.setattr(question_similarity, "similarity_score", _boom)
    assert question_similarity.is_topically_relevant("질문", "기술_직무역량", "백엔드") is True
    assert question_similarity._cooldown_until > 0.0

    def _should_not_be_called(*args, **kwargs):
        raise AssertionError("쿨다운 중에는 similarity_score가 호출되면 안 된다")

    monkeypatch.setattr(question_similarity, "similarity_score", _should_not_be_called)
    assert question_similarity.is_topically_relevant("질문", "기술_직무역량", "백엔드") is True
