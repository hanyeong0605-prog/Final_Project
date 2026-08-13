"""question_similarity.py의 코퍼스 임베딩 캐시를 한 번에, 천천히 채워두는 워밍업 스크립트.

2026-08-12 배경: 캐시가 비어있는 상태에서 질문을 검증할 때마다 해당 (category, job) 풀
전체(수십~수백 개)를 매번 새로 임베딩하려고 시도하다가, 그게 채 끝나기도 전에 Gemini
Embedding API 무료 티어 할당량(분당 100회 / 일일 1000회)을 금방 다 써버리는 문제를
실사용 중 발견했다(캐시 파일 자체가 생성된 적이 없었음 - question_similarity.py의
fail-open이 매번 조용히 삼켜서 겉으로는 원인이 안 보였다).

이 스크립트는 있는 코퍼스 풀 전체를 요청 사이 텀을 주면서 순서대로 딱 한 번씩만 임베딩해서
디스크 캐시(question_similarity._CACHE_PATH)에 저장한다 - 한 번 성공하면 그 다음부턴
실제 질문 생성 때마다 후보 질문 1개만 임베딩하면 되므로(풀은 캐시에서 읽음) 평소 사용량이
확 줄어든다.

사용법(ai-server 폴더에서, venv 활성화한 상태, GEMINI_API_KEY 할당량이 남아있을 때):
    python ml/warm_up_corpus_embeddings.py
"""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview import question_similarity  # noqa: E402
from app.domain.interview.question_generator import (  # noqa: E402
    FIELD_SENSITIVE_CATEGORIES,
    QUESTION_CATEGORIES,
)

# test_field_questions.py / test_field_questions_validated.py와 동일한 5개 분야 라벨.
FIELDS = ["백엔드", "프론트엔드", "풀스택", "모바일 (iOS/Android)", "데이터 · AI · 기타"]

# 분당 100회 한도를 안 건드리게, 큰 풀(수백 개 -> 배치 여러 번) 사이에 여유를 둔다.
_PAUSE_SECONDS = 8

targets: list[tuple[str, str]] = []
for category in QUESTION_CATEGORIES:
    if category in FIELD_SENSITIVE_CATEGORIES:
        targets.extend((category, field) for field in FIELDS)
    else:
        targets.append((category, ""))

print(f"임베딩할 (카테고리, 분야) 조합 {len(targets)}개:")
for category, job in targets:
    print(f"  - {category!r} / {job or '(분야무관)'}")

for i, (category, job) in enumerate(targets, start=1):
    print(f"\n[{i}/{len(targets)}] {category} / {job or '(분야무관)'} 임베딩 중...")
    try:
        pool, embeddings = question_similarity._get_pool_embeddings(category, job)
        print(f"  -> 완료 ({len(pool)}개 질문)")
    except Exception as exc:
        print(f"  -> 실패: {type(exc).__name__}: {exc}")
        print("  할당량이 아직 남아있는지 확인하고 나중에 다시 돌려줘 - 지금까지 성공한 "
              "조합은 캐시에 이미 저장돼 있으니 처음부터 다시 할 필요는 없어(이어서 진행됨).")
        break
    if i < len(targets):
        time.sleep(_PAUSE_SECONDS)

print("\n워밍업 끝. app/domain/interview/model/corpus_embeddings_cache.json 파일 생겼는지 확인해봐.")
