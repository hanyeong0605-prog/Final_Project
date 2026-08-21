"""question_similarity.is_topically_relevant() / similarity_score() 단위 테스트.

2026-08-20: Gemini Embedding API 의존을 없애고 로컬 TF-IDF(scikit-learn, char n-gram)로
바꾼 뒤의 테스트다.

주제 판별(유사/비유사) 테스트는 question_corpus.get_pool()을 목킹하지 않고 실제 코퍼스를
그대로 쓴다 - TF-IDF의 IDF 통계는 풀 크기가 작으면(직접 지어낸 2~3개짜리 장난감 풀)
제대로 작동하지 않아서(문서 빈도 계산이 무의미해짐) 실제 운영 환경(풀 크기 117~500개)과
다르게 움직이기 때문에, 진짜 판별 성능을 보려면 진짜 풀 크기로 검증해야 한다.
(test_question_corpus.py가 전역 캐시를 가짜 데이터로 오염시켜 두는 채로 남겨두던 버그를
같이 고쳤으니 - monkeypatch.setattr(question_corpus, "_pools", None) 사용 - 이제 이
파일 실행 순서와 무관하게 항상 진짜 코퍼스를 읽는다.) 캐싱/빈 풀 같은 구조적인 동작은
풀 내용과 무관하므로 목킹한 풀로 검증한다."""

from app.domain.interview import question_corpus, question_similarity


def _setup_pool(monkeypatch, pool):
    monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: pool)
    # 풀이 바뀌었는데 이전 테스트가 남긴 인메모리 캐시를 재사용하지 않도록 매 테스트마다
    # 초기화한다 - _get_pool_vectorizer는 (category, job) 키로만 캐시하므로, 다른 테스트가
    # 같은 category/job 조합을 다른 내용의 풀로 썼다면 캐시 히트로 착각할 수 있다.
    monkeypatch.setattr(question_similarity, "_vectorizer_cache", {})


def test_empty_pool_returns_none_score_and_is_relevant(monkeypatch):
    """비교할 코퍼스 풀이 없으면(그 category/job 조합 데이터가 아예 없음) 검증을 포기하고
    True를 돌려줘야 한다 - 기준이 없는데 억지로 떨어뜨리면 안 된다."""
    _setup_pool(monkeypatch, [])

    assert question_similarity.similarity_score("아무 질문", "기술_직무역량", "백엔드") is None
    assert question_similarity.is_topically_relevant("아무 질문", "기술_직무역량", "백엔드") is True


def test_similar_question_passes_threshold(monkeypatch):
    """실제 백엔드 풀(question_corpus.py, 120개) 기준 - 같은 주제의 다른 표현은 통과해야 한다."""
    monkeypatch.setattr(question_similarity, "_vectorizer_cache", {})

    score = question_similarity.similarity_score(
        "JPA N+1 문제 해결 경험을 말씀해 주세요.", "기술_직무역량", "백엔드"
    )
    assert score is not None and score > question_similarity.SIMILARITY_THRESHOLD
    assert question_similarity.is_topically_relevant(
        "JPA N+1 문제 해결 경험을 말씀해 주세요.", "기술_직무역량", "백엔드"
    ) is True


def test_dissimilar_question_fails_threshold(monkeypatch):
    """question_corpus.py 설계 메모에 나온 실제 오탐 사례 그대로 검증한다(실제 모바일
    풀 120개 기준) - '모바일' 요청인데 'GitHub와 Docker의 차이점' 같은 완전히 동떨어진
    주제가 섞여 나오는 경우 걸러내야 한다."""
    monkeypatch.setattr(question_similarity, "_vectorizer_cache", {})

    assert question_similarity.is_topically_relevant(
        "GitHub와 Docker의 차이점을 설명해 주세요.", "기술_직무역량", "모바일 (iOS/Android)"
    ) is False


def test_exact_match_scores_near_one(monkeypatch):
    """풀에 있는 질문과 완전히 같은 문장이면 유사도가 1에 가까워야 한다(자기 자신과의
    비교이므로) - 점수 계산 자체가 뒤집혀 있지 않은지 확인하는 회귀 테스트."""
    pool = [f"샘플 질문 {i}번, 서로 다른 내용을 담고 있습니다." for i in range(10)]
    pool[0] = "JPA N+1 문제를 설명해 주세요."
    _setup_pool(monkeypatch, pool)

    score = question_similarity.similarity_score("JPA N+1 문제를 설명해 주세요.", "기술_직무역량", "백엔드")
    assert score > 0.99


def test_pool_vectorizer_is_cached_in_memory(monkeypatch):
    """같은 풀(내용 동일)로 두 번 호출하면 두 번째는 벡터라이저 재학습 없이 캐시를
    재사용해야 한다."""
    pool = ["JPA N+1 문제를 설명해 주세요.", "REST API 설계 원칙은 무엇인가요?"]
    _setup_pool(monkeypatch, pool)

    question_similarity.similarity_score("아무 질문", "기술_직무역량", "백엔드")
    key = question_similarity._pool_cache_key("기술_직무역량", "백엔드")
    _, vectorizer1, matrix1 = question_similarity._vectorizer_cache[key]

    question_similarity.similarity_score("다른 질문", "기술_직무역량", "백엔드")
    _, vectorizer2, matrix2 = question_similarity._vectorizer_cache[key]

    assert vectorizer1 is vectorizer2  # 재학습되지 않고 그대로 재사용됨
    assert matrix1 is matrix2


def test_pool_changes_invalidate_cache(monkeypatch):
    """같은 (category, job) 키라도 풀 내용 자체가 바뀌면(코퍼스 갱신 등) 캐시를 쓰지 않고
    다시 학습해야 한다."""
    monkeypatch.setattr(question_similarity, "_vectorizer_cache", {})
    monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["질문 A"])
    question_similarity.similarity_score("아무 질문", "기술_직무역량", "백엔드")

    monkeypatch.setattr(question_corpus, "get_pool", lambda category, job: ["질문 A", "질문 B"])
    pool, vectorizer, matrix = question_similarity._get_pool_vectorizer("기술_직무역량", "백엔드")
    assert pool == ["질문 A", "질문 B"]
