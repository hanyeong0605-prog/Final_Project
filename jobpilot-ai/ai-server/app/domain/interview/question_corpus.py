"""LoRA가 생성한 질문의 "진짜 질문 풀" - 검증 실패 시 대체용 + 유사도 비교 기준.

2026-08-07 배경: LoRA(question_generator.py)가 재학습(rank 16, 분야당 120개) 이후로도
가끔 분야를 벗어난 질문을 만든다(예: "모바일" 요청인데 "GitHub와 Docker의 차이점" 같은
완전히 다른 주제가 섞여 나옴 - test_field_questions.py로 직접 확인함). 재학습으로 빈도는
크게 줄었지만 0%는 아니라서, question_similarity.py가 생성 결과를 이 모듈의 코퍼스와
임베딩 유사도로 비교해 기준 미달이면 여기서 실제 질문 하나를 뽑아 조용히 대체한다
(question_generator.generate_validated_question 참고).

코퍼스 원본은 LoRA 학습에 썼던 바로 그 파일(ml/interview_qa_pairs_categorized_with_fields.jsonl)
이다 - AI Hub 실제 면접 질문 + Gemini로 생성한 분야별 기술 질문이 섞여 있고, 전부 사람이
한 번씩 검수를 거쳤으므로(생성 스크립트의 데이터 정제 단계 참고) "이상한 질문이 나올 걱정이
없는" 안전한 대체 후보 풀로 쓸 수 있다.

2026-08-07: ml/ 원본이 아니라 이 파일과 같은 도메인 아래(data/)에 사본을 두고 그걸 읽는다 -
Dockerfile이 `COPY app ./app`만 하고 ml/은 이미지에 아예 안 들어가서(배포용 컨테이너 크기를
줄이려는 의도, LoRA 모델 가중치를 이미지에서 뺀 것과 같은 이유), ml/ 경로를 그대로 쓰면
배포 환경에서 코퍼스 폴백 자체가 통째로 동작하지 않는 문제가 있었다. 이 데이터는 무거운
모델 가중치와 달리 용량이 작은 텍스트라 이미지에 포함시키는 데 부담이 없다 - 오히려 Gemini도
LoRA도 다 실패했을 때 마지막 보루로 반드시 배포 환경에 있어야 하는 파일이다
(question_generator.generate_validated_question 참고). 재학습용 원본은 ml/에 그대로 두고,
데이터를 새로 늘릴 때(generate_field_technical_questions.py 재실행 등)는 이 사본도
같이 갱신해야 한다.
"""

import json
import random
from pathlib import Path

from app.domain.interview.question_generator import DEFAULT_JOB, FIELD_SENSITIVE_CATEGORIES

CORPUS_PATH = Path(__file__).parent / "data" / "interview_qa_pairs_categorized_with_fields.jsonl"

# (category, job) -> 질문 목록. FIELD_SENSITIVE_CATEGORIES가 아닌 카테고리는 job을 구분할
# 필요가 없으므로("" ) 하나의 풀로 합친다 - question_generator.py의 FIELD_SENSITIVE_CATEGORIES
# 설계 메모(인성/역량 계열은 분야 무관) 참고.
_pools: dict[tuple[str, str], list[str]] | None = None


def _load_pools() -> dict[tuple[str, str], list[str]]:
    pools: dict[tuple[str, str], list[str]] = {}
    if not CORPUS_PATH.exists():
        return pools
    with CORPUS_PATH.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            category = row.get("category", "")
            question = row.get("question", "")
            if not category or not question:
                continue
            job = row.get("job", "") if category in FIELD_SENSITIVE_CATEGORIES else ""
            pools.setdefault((category, job), []).append(question)
    return pools


def _get_pools() -> dict[tuple[str, str], list[str]]:
    global _pools
    if _pools is None:
        _pools = _load_pools()
    return _pools


def get_pool(category: str, job: str) -> list[str]:
    """category(+분야별 카테고리면 job까지) 기준으로 실제 질문 목록을 돌려준다.

    분야별 카테고리(FIELD_SENSITIVE_CATEGORIES)인데 job이 5개 라벨(백엔드/프론트엔드/...) 중
    하나가 아니면(예: 프로필 미입력으로 DEFAULT_JOB이 온 경우) 그 분야 전용 풀이 없으므로,
    분야 구분 없이 두루 쓰이던 기존 공통 풀(job=DEFAULT_JOB)로 폴백한다.
    """
    pools = _get_pools()
    if category in FIELD_SENSITIVE_CATEGORIES:
        pool = pools.get((category, job))
        if pool:
            return pool
        return pools.get((category, DEFAULT_JOB), [])
    return pools.get((category, ""), [])


def pick_question(category: str, job: str, exclude: set[str]) -> str | None:
    """category/job 풀에서 exclude(이번 세션에서 이미 나온 질문들)에 없는 질문 하나를
    무작위로 골라 돌려준다 - 무료 등급의 1차 질문 소스이자, 유료 등급이 Gemini 실패로
    코퍼스에 기댈 때도 같은 함수를 써서 세션 안에서 중복이 안 나오게 한다(기존
    generate_validated_question의 random.choice(pool)은 세션 내 다른 호출 결과를 몰라서
    같은 풀에서 중복으로 뽑힐 수 있었다).

    풀에서 exclude 뺀 후보가 하나도 안 남으면(풀이 세션 질문 수보다 작은 극단적인 경우)
    "중복 없음"보다 "질문이 아예 안 나오는 것"을 막는 게 우선이라 exclude를 무시하고 풀
    전체에서 다시 고른다 - 이 경우만 중복이 남을 수 있다."""
    pool = get_pool(category, job)
    if not pool:
        return None
    candidates = [q for q in pool if q not in exclude]
    return random.choice(candidates or pool)


def reload_pools() -> None:
    """코퍼스 파일이 갱신된 뒤(재학습 데이터 추가 등) 캐시를 강제로 다시 읽고 싶을 때 쓴다.
    서버는 보통 재시작되므로 평소엔 안 써도 되고, 테스트에서 CORPUS_PATH를 바꿔치기할 때
    쓰기 위한 용도가 크다."""
    global _pools
    _pools = None
