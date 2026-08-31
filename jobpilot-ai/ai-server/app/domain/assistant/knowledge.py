"""사이트 챗봇(assistant)용 RAG 지식 검색.

2026-08-20 설계: chat.py는 원래 사이트 페이지 목록(site_map.py, 15개 고정 목록)만 프롬프트에
넣고, 나머지 질문("구독 요금이 얼마냐", "기업회원 승인은 어떻게 되냐" 같은 우리 사이트
고유의 정책/기능 질문)은 전부 Gemini 자체 지식에 맡기고 있었다 - Gemini는 이 사이트의
실제 정책을 알 수 없으니 얼버무리거나 지어낼 위험이 있었다.

이 모듈은 사용자 메시지와 관련 있는 사이트 지식 조각(data/site_knowledge.jsonl - 실제
코드/정책에서 뽑은 FAQ 형태 문서)을 검색해서 chat.py가 프롬프트에 근거로 끼워넣을 수 있게
해준다. question_similarity.py와 완전히 같은 이유로 같은 방식(로컬 TF-IDF, 문자 2~4-gram,
외부 API 호출 없음)을 쓴다 - 지식 조각이 십수 개 수준이라 굳이 임베딩 API를 쓸 필요가
없고, 그 API를 썼다가 겪었던 할당량 문제를 애초에 만들지 않기 위함이기도 하다.

지식 조각 개수가 적어서(question_corpus.py의 코퍼스 풀보다도 작음) 매 요청마다 다시
벡터화해도 비용이 무시할 만하지만, 반복 호출을 아끼기 위해 모듈 전체를 한 번만 벡터화해서
캐시해둔다(질문마다 (category, job) 조합이 갈리는 question_similarity.py와 달리 여기는
지식 조각 전체가 항상 하나의 풀이라 캐시 키가 필요 없다)."""

import json
from pathlib import Path

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sqlalchemy import text

from app.core.db import get_engine

_KNOWLEDGE_PATH = Path(__file__).parent / "data" / "site_knowledge.jsonl"

# question_similarity.py에서 실제 코퍼스로 확인한 것과 같은 척도(문자 2~4-gram 코사인
# 유사도) - 완전히 무관한 질문과 관련 있는 질문 사이에 대략 0.2~0.3 부근에서 경계가
# 생기는 걸 확인했다. 여기 지식 조각은 문장이 더 길고 항목 수도 적어서 분포가 살짝
# 다를 수 있으니, 배포 후 실제 로그(아래 print)를 보면서 조정이 필요할 수 있다.
SIMILARITY_THRESHOLD = 0.15
# 사이트 지식 조각 개수가 적어서(수십 개 이내) 관련도 상위 몇 개만 있으면 충분하다 -
# 너무 많이 끼워넣으면 프롬프트만 길어지고 Gemini가 정말 관련 있는 근거를 찾기 어려워진다.
TOP_K = 3

_cache: tuple[list[dict], TfidfVectorizer, object] | None = None


def _load_file_knowledge() -> list[dict]:
    if not _KNOWLEDGE_PATH.exists():
        return []
    entries: list[dict] = []
    with _KNOWLEDGE_PATH.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            topic = row.get("topic", "")
            text = row.get("text", "")
            if topic and text:
                entries.append({"topic": topic, "text": text})
    return entries


def _load_database_knowledge() -> list[dict]:
    """Load only global, administrator-curated assistant knowledge.

    The AI-server chat endpoint does not receive an authenticated member ID, so
    it must never load MEMBER-scoped resumes, specs, or private finance notes.
    Those require a future authenticated backend proxy. If Flyway has not yet
    created the table or the database is temporarily unavailable, the checked-in
    knowledge file keeps the chat fail-open.
    """
    try:
        with get_engine().connect() as connection:
            rows = connection.execute(text("""
                SELECT title, content
                FROM assistant_knowledge_documents
                WHERE scope = 'GLOBAL' AND is_active = TRUE
                ORDER BY id
            """)).mappings()
            return [
                {"topic": str(row["title"]), "text": str(row["content"])}
                for row in rows
                if row["title"] and row["content"]
            ]
    except Exception:
        return []


def _load_knowledge() -> list[dict]:
    """Prefer the database index; retain the packaged file during rollout."""
    database_entries = _load_database_knowledge()
    return database_entries or _load_file_knowledge()


def _get_vectorizer() -> tuple[list[dict], TfidfVectorizer | None, object]:
    global _cache
    if _cache is not None:
        return _cache

    entries = _load_knowledge()
    if not entries:
        _cache = (entries, None, None)
        return _cache

    # topic + text를 같이 벡터화한다 - 주제어(topic)가 질문의 핵심 키워드와 직접 겹치는
    # 경우가 많아서(예: 질문 "구독 얼마야" vs topic "구독 요금") 검색 정확도에 도움이 된다.
    documents = [f"{e['topic']} {e['text']}" for e in entries]
    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4))
    matrix = vectorizer.fit_transform(documents)
    _cache = (entries, vectorizer, matrix)
    return _cache


def search(query: str, top_k: int = TOP_K) -> list[dict]:
    """query와 관련 있는 사이트 지식 조각을 관련도 순으로 최대 top_k개 돌려준다.
    각 항목은 {"topic": str, "text": str, "score": float} 형태. 지식 베이스가 비어있거나
    관련도가 SIMILARITY_THRESHOLD 미만이면 빈 리스트를 돌려준다 - 억지로 무관한 내용을
    근거인 척 끼워넣지 않기 위함이다."""
    query = (query or "").strip()
    if not query:
        return []

    entries, vectorizer, matrix = _get_vectorizer()
    if vectorizer is None:
        return []

    scores = cosine_similarity(vectorizer.transform([query]), matrix)[0]
    ranked = sorted(range(len(entries)), key=lambda i: scores[i], reverse=True)

    results = []
    for i in ranked[:top_k]:
        score = float(scores[i])
        if score < SIMILARITY_THRESHOLD:
            break  # 내림차순 정렬이므로 여기서부터는 전부 임계값 미만
        results.append({"topic": entries[i]["topic"], "text": entries[i]["text"], "score": score})
    return results


def knowledge_prompt_block(query: str) -> str:
    """chat.py가 프롬프트에 그대로 끼워넣을 수 있는 문자열을 돌려준다. 관련 지식이 없으면
    빈 문자열(호출부가 그 경우 섹션 자체를 프롬프트에서 뺄 수 있게)."""
    matches = search(query)
    if not matches:
        return ""
    return "\n".join(f"- {m['topic']}: {m['text']}" for m in matches)
