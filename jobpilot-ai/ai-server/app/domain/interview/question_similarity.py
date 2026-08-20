"""LoRA가 생성한 질문이 해당 분야/카테고리에 실제로 어울리는지 임베딩 유사도로 검증한다.

2026-08-07 설계 메모:
- question_generator.py의 LoRA는 "~하지 마라" 같은 자연어 지시를 이해하고 지킬 능력이 없다
  (지시 따르기 훈련을 받은 적 없는, 200~600개 예시로 패턴만 학습한 작은 모델이라서) - 그래서
  프롬프트로 타이르는 방식 대신, 결과가 나온 *뒤에* 기계적으로 검사하는 방식을 쓴다.
- 검사 기준은 "우리가 갖고 있는 진짜 질문들(question_corpus.py)과 의미적으로 얼마나 비슷한가"다.
  단순 키워드 대조 대신 임베딩 코사인 유사도를 쓰는 이유: "GitHub와 Docker의 차이점을 설명해
  주세요"처럼 특정 금지 단어가 있는 게 아니라 주제 자체가 통째로 다른 경우까지 잡아내야 해서다.
- 임베딩은 로컬에 별도 모델(sentence-transformers 등)을 새로 설치하는 대신 Gemini Embedding
  API(client.models.embed_content, model="gemini-embedding-001")를 쓴다 - LoRA가 도는 기기
  (Tailscale로 연결된 로컬 컴퓨터, ai-server ml/generate_field_technical_questions.py의
  Gemini 데이터 생성 스크립트와 동일한 GEMINI_API_KEY 재사용)에 무거운 새 모델을 또 얹지
  않아도 되고, question_generator.py의 _gemini_polish()가 이미 매 LoRA 결과마다 Gemini를
  한 번 호출하는 구조라 API 의존성이 새로 생기는 것도 아니다.
- 코퍼스 풀 전체를 매 요청마다 다시 임베딩하면 느리고 API 호출도 낭비이므로, 풀 단위로 한 번만
  임베딩해서 디스크에 캐시해둔다(_CACHE_PATH) - 코퍼스 파일이 안 바뀌는 한 서버를 껐다 켜도
  재사용된다.
"""

import hashlib
import json
import math
import time
from pathlib import Path

from app.core.config import settings
from app.domain.interview import question_corpus

_CACHE_PATH = Path(__file__).parent / "model" / "corpus_embeddings_cache.json"
_EMBED_MODEL = "gemini-embedding-001"
# 한 번의 embed_content 호출에 너무 많은 텍스트를 몰아넣으면 요청 크기 제한에 걸릴 수 있어
# 방어적으로 쪼갠다 - 기술_직무역량의 공통 풀(500개)이 제일 큰 풀이라 이 값이 필요하다.
_EMBED_BATCH_SIZE = 100
# 코사인 유사도 임계값 - 1에 가까울수록 엄격. 0.5는 "완전히 다른 주제는 걸러내되, 같은
# 분야 안에서의 표현 차이는 너그럽게 봐준다"는 선에서 잡은 시작값이었다. 실제 배포 후 오탐/누락
# 비율을 보고 조정이 필요할 수 있다(코드 밖에서 값만 바꾸면 되도록 상수로 뺐다).
# 2026-08-12: 0.5에서는 "AWS 스토리지(NAS) 장애 경험"(모바일로 요청) 같은 - 개념 자체는
# 실존하지만 분야가 명백히 다른 - 질문이 그대로 통과되는 사례가 실제 테스트(test_field_
# questions_validated.py)에서 확인됐다. 0.6으로 올려서 더 엄격하게 걸러지는지 확인 중 -
# 반대로 너무 올리면 같은 분야의 정상 질문까지 코퍼스로 대체되는 오탐이 늘 수 있으니,
# 아래 print 로그(score)로 실제 값을 보면서 재조정해야 한다.
SIMILARITY_THRESHOLD = 0.6

# 2026-08-20: Gemini Embedding 무료 티어 할당량(분당/일일)을 8/13에 실제로 소진시킨 원인 수정.
# 캐시가 비어있는 채로 여러 요청이 몰리면 같은 풀을 계속 재시도하며 할당량을 반복 소모하므로,
# 임베딩 호출이 한 번 실패하면 이 시간 동안은 검증 자체를 건너뛰고 fail-open으로 바로 통과시킨다
# (여러 요청이 동시에 재시도를 폭주시키는 것을 막는 쿨다운).
_COOLDOWN_SECONDS = 300
_cooldown_until = 0.0


def _pool_cache_key(category: str, job: str) -> str:
    return f"{category}|{job}"


def _pool_hash(questions: list[str]) -> str:
    return hashlib.sha256("\n".join(questions).encode("utf-8")).hexdigest()


def _load_cache() -> dict:
    if not _CACHE_PATH.exists():
        return {}
    try:
        return json.loads(_CACHE_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}


def _save_cache(cache: dict) -> None:
    _CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    _CACHE_PATH.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")


def _embed_texts(texts: list[str]) -> list[list[float]]:
    """Gemini Embedding API로 텍스트 목록을 임베딩한다 - 호출 실패 시 예외를 그대로 던진다
    (호출부인 is_topically_relevant가 fail-open으로 처리)."""
    from google import genai

    client = genai.Client(api_key=settings.gemini_api_key)
    vectors: list[list[float]] = []
    for start in range(0, len(texts), _EMBED_BATCH_SIZE):
        chunk = texts[start : start + _EMBED_BATCH_SIZE]
        result = client.models.embed_content(model=_EMBED_MODEL, contents=chunk)
        vectors.extend(embedding.values for embedding in result.embeddings)
    return vectors


def _get_pool_embeddings(category: str, job: str) -> tuple[list[str], list[list[float]]]:
    """category/job에 해당하는 코퍼스 풀과, 그 풀 각 질문의 임베딩 벡터를 돌려준다.
    캐시에 없거나 코퍼스 내용이 바뀌었으면(해시 불일치) 새로 임베딩해서 캐시에 저장한다.

    2026-08-20: 기존에는 풀 전체(최대 500개, 5회 호출) 임베딩이 전부 끝나야만 캐시에
    저장됐다 - 중간에 rate limit으로 실패하면 아무것도 저장되지 않아, 다음 요청이 또
    처음부터 500개를 재시도하며 할당량을 반복 소모하는 문제가 있었다(8/13 할당량 소진
    사고의 원인). 이제는 이미 캐시된 부분이 있으면 그 뒤부터 이어서 임베딩하고, 배치가
    끝날 때마다(전체가 안 끝나도) 그때까지의 진행분을 캐시에 저장한다 - 여러 요청에 걸쳐
    조금씩이라도 반드시 앞으로 나아가고, 완성된 뒤로는 다시 API를 호출하지 않는다."""
    pool = question_corpus.get_pool(category, job)
    if not pool:
        return [], []

    key = _pool_cache_key(category, job)
    pool_hash = _pool_hash(pool)
    cache = _load_cache()
    cached_entry = cache.get(key)

    already: list[list[float]] = []
    if cached_entry and cached_entry.get("hash") == pool_hash:
        already = cached_entry["embeddings"]
        if len(already) >= len(pool):
            return pool, already

    remaining = pool[len(already):]
    embeddings = list(already)
    try:
        for start in range(0, len(remaining), _EMBED_BATCH_SIZE):
            chunk = remaining[start : start + _EMBED_BATCH_SIZE]
            embeddings.extend(_embed_texts(chunk))
            # 배치 하나가 끝날 때마다 즉시 저장 - 다음 배치에서 실패해도 이 진행분은 남는다.
            cache[key] = {"hash": pool_hash, "embeddings": embeddings}
            _save_cache(cache)
    except Exception:
        if len(embeddings) > len(already):
            cache[key] = {"hash": pool_hash, "embeddings": embeddings}
            _save_cache(cache)
        raise

    return pool, embeddings


def _cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(y * y for y in b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


def similarity_score(question: str, category: str, job: str) -> float | None:
    """question과 코퍼스 풀(category/job) 중 가장 비슷한 질문 사이의 코사인 유사도(최댓값)를
    돌려준다. 비교할 풀 자체가 없으면(코퍼스에 그 category/job 조합이 없음) None을 돌려준다
    - 이 경우 호출부는 검증을 건너뛰고 원본을 그대로 신뢰해야 한다(비교 기준이 없는데 억지로
    떨어뜨리면 안 됨)."""
    pool, pool_embeddings = _get_pool_embeddings(category, job)
    if not pool_embeddings:
        return None
    candidate_embedding = _embed_texts([question])[0]
    return max(_cosine_similarity(candidate_embedding, vec) for vec in pool_embeddings)


def is_topically_relevant(question: str, category: str, job: str) -> bool:
    """question이 해당 분야/카테고리 주제에서 크게 벗어나지 않았으면 True.

    fail-open: Gemini Embedding 호출 자체가 실패하면(키 없음, 네트워크 오류 등) 검증을
    포기하고 True를 돌려준다 - question_generator.py의 다른 Gemini 연동(_gemini_polish 등)과
    같은 설계 원칙으로, 검증 인프라 장애가 곧 "질문 생성 실패"로 이어지면 안 된다."""
    if not settings.gemini_api_key:
        print("[유사도검증] gemini_api_key가 비어있어서 검증을 건너뜀")
        return True

    global _cooldown_until
    now = time.monotonic()
    if now < _cooldown_until:
        # 최근에 임베딩 API가 실패해서(주로 rate limit) 쿨다운 중 - 재시도로 할당량을
        # 더 태우지 않고 바로 fail-open으로 넘어간다.
        return True

    try:
        score = similarity_score(question, category, job)
    except Exception as exc:
        _cooldown_until = now + _COOLDOWN_SECONDS
        # 2026-08-12: 튜닝 중 - fail-open으로 조용히 넘어가던 예외를 눈에 보이게 임시로 출력.
        # 튜닝 끝나면 이 print는 지우거나 logger.warning으로 낮춰도 된다.
        print(f"[유사도검증] 예외로 fail-open 처리됨 (다음 {_COOLDOWN_SECONDS}초간 검증 건너뜀): "
              f"{type(exc).__name__}: {exc}")
        return True
    if score is None:
        return True
    passed = score >= SIMILARITY_THRESHOLD
    # 2026-08-12: 임계값 튜닝 중 - 실제 점수를 눈으로 보면서 SIMILARITY_THRESHOLD를 맞추기
    # 위한 임시 로그. 튜닝 끝나면 print 대신 logger.debug로 낮추거나 제거해도 된다.
    print(f"[유사도검증] job={job!r} category={category!r} score={score:.3f} "
          f"threshold={SIMILARITY_THRESHOLD} -> {'통과' if passed else '코퍼스로 대체'}")
    return passed
