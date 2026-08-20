"""LoRA가 생성한 질문이 해당 분야/카테고리에 실제로 어울리는지 로컬 TF-IDF 유사도로 검증한다.

2026-08-07 설계 메모:
- question_generator.py의 LoRA는 "~하지 마라" 같은 자연어 지시를 이해하고 지킬 능력이 없다
  (지시 따르기 훈련을 받은 적 없는, 200~600개 예시로 패턴만 학습한 작은 모델이라서) - 그래서
  프롬프트로 타이르는 방식 대신, 결과가 나온 *뒤에* 기계적으로 검사하는 방식을 쓴다.
- 검사 기준은 "우리가 갖고 있는 진짜 질문들(question_corpus.py)과 얼마나 비슷한가"다.
  단순 키워드 대조 대신 유사도 점수를 쓰는 이유: "GitHub와 Docker의 차이점을 설명해
  주세요"처럼 특정 금지 단어가 있는 게 아니라 주제 자체가 통째로 다른 경우까지 잡아내야 해서다.

2026-08-20 재설계 (Gemini Embedding API 제거):
- 기존에는 이 비교를 Gemini Embedding API(client.models.embed_content)로 했다. 그런데
  이 프로젝트는 배포가 잦고(docker-compose.prod.yml에 ai-server용 볼륨 마운트가 없어서
  배포=컨테이너 재생성=캐시 파일 소실), 코퍼스 풀 중 제일 큰 게 500개라 배포 직후 첫
  요청마다 500개를 API로 새로 임베딩해야 했다. 8/13에 무료 티어 할당량을 통째로
  소진시킨 사고가 이 구조 때문에 일어났다 - 캐시를 영구 저장하는 정도로는 "배포가 잦다"는
  이 프로젝트 특성상 근본 해결이 안 된다고 보고, 아예 외부 API 호출 자체를 없앴다.
- 대신 이미 requirements.txt에 있는 scikit-learn의 TfidfVectorizer로 로컬에서 계산한다.
  API 토큰을 전혀 쓰지 않으므로 풀 크기가 얼마든, 요청이 얼마나 몰리든 할당량 걱정이 없다.
- analyzer="char_wb"(문자 2~4-gram)를 쓰는 이유: 한국어는 조사/어미가 붙어서 단어 단위
  토큰화(예: "문제를" vs "문제가")로는 같은 어근도 다른 토큰으로 갈리기 쉬운데, 문자
  n-gram은 형태소 분석기 없이도 그 어근 겹침을 어느 정도 잡아낸다. Gemini 임베딩만큼
  정교한 의미 비교는 아니지만, 이 모듈의 목적(완전히 동떨어진 주제를 걸러내는 것)에는
  충분한 것으로 보고 시작한다 - 부족하면 SIMILARITY_THRESHOLD를 조정하거나 다른 방식을
  다시 검토한다.
- 풀 벡터화는 카테고리/직무 조합별로 인메모리에 캐시한다(서버 실행 중 코퍼스 파일이
  안 바뀌므로 (category, job) 키만으로 충분하다) - 디스크 캐시나 만료 로직이 필요 없다.
"""

from app.domain.interview import question_corpus

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# TF-IDF(문자 2~4-gram) 코사인 유사도 임계값. Gemini 임베딩 때 쓰던 0.6과는 척도가 전혀
# 다르다(문자 n-gram 기반이라 값 분포 자체가 다름) - 실제 코퍼스로 확인한 결과, 같은 분야
# 안에서의 표현 차이는 대략 0.27~0.64, 완전히 다른 주제는 0.2~0.33 사이에서 나타났다.
# 분야별 풀(120개 안팎)은 구분이 잘 되지만, job 미指정 시 폴백되는 공통 풀(500개, 여러
# 분야가 섞여 있음)은 원래도 폭이 좁아서 오탐/누락이 상대적으로 더 있을 수 있다 - 실제
# 배포 후 아래 print 로그(score)로 값을 보면서 조정이 필요할 수 있다(코드 밖에서 값만
# 바꾸면 되도록 상수로 뺐다).
SIMILARITY_THRESHOLD = 0.3

# (category, job) -> (pool, vectorizer, tfidf_matrix). 풀 내용이 서버 실행 중 바뀌지
# 않는다는 전제(question_corpus.py와 동일)로, 같은 풀이면 재계산하지 않고 재사용한다.
_vectorizer_cache: dict[str, tuple[list[str], TfidfVectorizer, object]] = {}


def _pool_cache_key(category: str, job: str) -> str:
    return f"{category}|{job}"


def _get_pool_vectorizer(category: str, job: str) -> tuple[list[str], TfidfVectorizer | None, object]:
    """category/job에 해당하는 코퍼스 풀과, 그 풀로 학습된 TF-IDF 벡터라이저·행렬을
    돌려준다. 풀이 없으면 (빈 리스트, None, None)."""
    pool = question_corpus.get_pool(category, job)
    if not pool:
        return [], None, None

    key = _pool_cache_key(category, job)
    cached = _vectorizer_cache.get(key)
    if cached is not None and cached[0] == pool:
        return cached

    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4))
    matrix = vectorizer.fit_transform(pool)
    _vectorizer_cache[key] = (pool, vectorizer, matrix)
    return pool, vectorizer, matrix


def similarity_score(question: str, category: str, job: str) -> float | None:
    """question과 코퍼스 풀(category/job) 중 가장 비슷한 질문 사이의 코사인 유사도(최댓값)를
    돌려준다. 비교할 풀 자체가 없으면(코퍼스에 그 category/job 조합이 없음) None을 돌려준다
    - 이 경우 호출부는 검증을 건너뛰고 원본을 그대로 신뢰해야 한다(비교 기준이 없는데 억지로
    떨어뜨리면 안 됨)."""
    pool, vectorizer, matrix = _get_pool_vectorizer(category, job)
    if vectorizer is None:
        return None
    candidate_vector = vectorizer.transform([question])
    return float(cosine_similarity(candidate_vector, matrix)[0].max())


def is_topically_relevant(question: str, category: str, job: str) -> bool:
    """question이 해당 분야/카테고리 주제에서 크게 벗어나지 않았으면 True.

    fail-open: 벡터화 과정에서 예상치 못한 예외가 나면(코퍼스 파일 손상 등) 검증을 포기하고
    True를 돌려준다 - question_generator.py의 다른 검증/생성 로직과 같은 설계 원칙으로,
    검증 인프라 장애가 곧 "질문 생성 실패"로 이어지면 안 된다. (2026-08-20: 이 검증은 더는
    외부 API를 쓰지 않으므로 rate limit류 예외는 사실상 없고, 이 처리는 순수 방어용이다.)"""
    try:
        score = similarity_score(question, category, job)
    except Exception as exc:
        print(f"[유사도검증] 예외로 fail-open 처리됨: {type(exc).__name__}: {exc}")
        return True
    if score is None:
        return True
    passed = score >= SIMILARITY_THRESHOLD
    # 2026-08-12: 임계값 튜닝 중 - 실제 점수를 눈으로 보면서 SIMILARITY_THRESHOLD를 맞추기
    # 위한 임시 로그. 튜닝 끝나면 print 대신 logger.debug로 낮추거나 제거해도 된다.
    print(f"[유사도검증] job={job!r} category={category!r} score={score:.3f} "
          f"threshold={SIMILARITY_THRESHOLD} -> {'통과' if passed else '코퍼스로 대체'}")
    return passed
