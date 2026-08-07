"""분야별 "기술_직무역량" 카테고리 학습 데이터 생성 스크립트.

2026-08-07 배경: LoRA 질문 생성 모델(question_generator.py)의 학습 데이터
(interview_qa_pairs_categorized.jsonl)가 전부 job="ICT 개발자(신입)" 한 종류뿐이라, 분야
(백엔드/프론트엔드/...)를 넘겨도 실제 질문 내용이 달라지지 않는 문제가 있었다. 그래서 지금은
런타임에 Gemini(generate_personalized_question)로 우회하고 있는데, 이건 매 요청마다 API
비용/지연이 발생한다 - "직접 학습시킨 모델"이 이 프로젝트의 차별점이기도 해서, 근본적으로는
LoRA 모델 자체가 분야를 반영하도록 재학습하는 게 맞다.

핵심 절감 포인트(question_generator.py의 INTERVIEW_TYPES/FIELD_SENSITIVE_CATEGORIES 설계
메모 참고): 인성/역량 계열 카테고리(가치관_자기관리, 협업_리더십_커뮤니케이션, 문제해결_도전경험,
강점_약점, 자기소개_지원동기)는 지원자의 경험/가치관을 묻는 거라 분야가 달라도 질문이 같아도
된다 - 기존 학습 데이터로 충분하다. 분야별로 진짜 다른 내용이 필요한 건 "기술_직무역량" 카테고리
하나뿐이다. 그래서 이 스크립트는 그 카테고리 하나만, 5개 분야로 나눠서 Gemini로 "학습용 데이터"를
1회성으로 만들어낸다 - 이건 서비스 런타임 호출이 아니라 오프라인 데이터 생성이라 사용자가 겪는
비용/지연과 무관하다.

사용법(로컬에서 실행 - 이 리포를 다루는 샌드박스 환경은 Gemini API 아웃바운드가 막혀 있어서
실행이 안 됨. ai-server 폴더에서 venv 활성화하고 아래처럼 실행):

    python ml/generate_field_technical_questions.py

실행하면 ml/interview_qa_pairs_field_technical.jsonl 이 새로 생긴다. 그 다음:

    cat ml/interview_qa_pairs_categorized.jsonl ml/interview_qa_pairs_field_technical.jsonl \
        > ml/interview_qa_pairs_categorized_with_fields.jsonl

로 합친 뒤, mock_interview_question_generator.ipynb의 4단계(questions 로딩)에서 이 합쳐진
파일을 읽도록 경로만 바꿔서 재학습하면 된다.
"""

import json
import sys
from pathlib import Path

# 2026-08-07: "python ml/generate_field_technical_questions.py"로 바로 실행하면 파이썬이
# 스크립트가 있는 ml/ 디렉터리만 sys.path에 넣어서 "app" 패키지(ai-server/app)를 못 찾는다
# (다른 ml/debug_*.py 스크립트들도 같은 문제가 있음 - 지금까지는 IDE의 소스 루트 설정에
# 의존해서 넘어간 것으로 보인다). "python -m ml.generate_field_technical_questions"처럼
# -m으로 실행하거나 IDE에서 실행해야 한다는 걸 몰라도 되게, 여기서 ai-server 루트를 직접
# sys.path에 추가해서 어떤 방식으로 실행해도 동작하게 만들었다.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import settings  # noqa: E402

# 2026-08-06 프론트(MockInterviewPage.tsx INTERVIEW_ROLE_OPTIONS)에서 실제로 넘기는 라벨과
# 반드시 똑같아야 한다 - 학습 데이터의 job 문자열과 런타임 요청의 job 문자열이 안 맞으면
# 모델이 그 값을 본 적이 없는 셈이라 조건화 효과가 없다.
FIELDS = ["백엔드", "프론트엔드", "풀스택", "모바일 (iOS/Android)", "데이터 · AI · 기타"]

CATEGORY = "기술_직무역량"

# 2026-08-07: 1차 학습(분야당 40개, rank 8) 결과를 test_field_questions.py로 직접 확인해보니
# 분야 경계가 흐릿해서(예: "프론트엔드" 질문에 C++/OOP 내용이 섞여 나옴) 데이터를 늘리기로
# 했다 - 분야당 120개(3배)로 올리고, LoRA rank도 노트북에서 8 -> 16으로 같이 올릴 예정.
QUESTIONS_PER_FIELD = 120

# 한 번의 Gemini 호출에 120개를 한꺼번에 시키면 응답이 길어질수록 뒤로 갈수록 품질/다양성이
# 떨어질 위험이 있어서, 40개씩 3번 나눠 부르고 합친다(분야당 총 호출 3회, 5개 분야면 15회 -
# 무료 티어 하루 한도에 비하면 아주 적은 양이다).
BATCH_SIZE = 40

OUTPUT_PATH = Path(__file__).parent / "interview_qa_pairs_field_technical.jsonl"


def _build_prompt(field: str, n: int) -> str:
    return (
        f"너는 IT 채용 면접관이다. '{field}' 직무 지원자에게 실제로 물어볼 법한 "
        f"'기술 역량'을 확인하는 한국어 면접 질문을 서로 겹치지 않게 {n}개 만들어라.\n"
        "규칙:\n"
        f"1) 모든 질문은 '{field}' 직무에서 실제로 자주 나올 법한 구체적인 기술/개념/실무 "
        "상황을 다뤄야 한다 - 다른 직무에도 그대로 쓸 수 있는 두루뭉술한 질문은 안 된다\n"
        "2) 같은 기술을 반복해서 여러 각도(개념 이해, 트러블슈팅 경험, 트레이드오프 판단, "
        "성능/설계 고민, 왜 그 기술을 선택했는지 등)에서 다양하게 물어봐라 - 한 유형에 "
        "치우치지 마라\n"
        "3) '~습니까/~니까/~나요/~주세요' 같은 정중한 면접 질문 어미로 끝내고 존댓말을 써라\n"
        "4) 각 질문은 20~70자 내외로 간결하게\n"
        "5) 결과는 질문 문자열만 담은 JSON 배열로만 출력해라 - 다른 설명/텍스트는 절대 붙이지 마라\n"
        f'예시 형식: ["질문1", "질문2", ...] (정확히 {n}개)'
    )


def generate_for_field(client, field: str, n: int) -> list[str]:
    from google.genai import types

    response = client.models.generate_content(
        model=settings.gemini_model,
        contents=_build_prompt(field, n),
        config=types.GenerateContentConfig(
            temperature=1.1,
            response_mime_type="application/json",
        ),
    )
    raw = (response.text or "").strip()
    try:
        items = json.loads(raw)
    except json.JSONDecodeError:
        print(f"  [경고] '{field}' 응답이 JSON 파싱 실패 - 건너뜀. 원문: {raw[:200]}")
        return []

    if not isinstance(items, list):
        print(f"  [경고] '{field}' 응답이 배열이 아님 - 건너뜀")
        return []

    questions = [q.strip() for q in items if isinstance(q, str) and q.strip()]
    return questions


def main() -> None:
    if not settings.gemini_api_key:
        raise SystemExit(
            "GEMINI_API_KEY가 .env에 없습니다. ai-server/.env에 키를 넣고 다시 실행하세요."
        )

    from google import genai

    client = genai.Client(api_key=settings.gemini_api_key)

    all_rows: list[dict] = []
    for field in FIELDS:
        print(f"[{field}] 생성 중...")
        seen: set[str] = set()
        field_questions: list[str] = []
        remaining = QUESTIONS_PER_FIELD
        while remaining > 0:
            batch_n = min(BATCH_SIZE, remaining)
            batch = generate_for_field(client, field, batch_n)
            new_ones = [q for q in batch if q not in seen]
            for q in new_ones:
                seen.add(q)
                field_questions.append(q)
            print(f"  -> {len(batch)}개 생성 (신규 {len(new_ones)}개, 누적 {len(field_questions)}개)")
            remaining -= batch_n
        for q in field_questions:
            all_rows.append({"job": field, "context": "", "question": q, "category": CATEGORY})

    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        for row in all_rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    print(f"\n총 {len(all_rows)}개 -> {OUTPUT_PATH}")
    print("생성된 파일을 한 번 훑어보고 이상한 질문(내용 중복/애매함)은 지운 뒤 재학습에 쓰는 걸 권장합니다.")


if __name__ == "__main__":
    main()
