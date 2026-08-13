"""generate_validated_question() (실제 서비스에 붙어있는 최종 경로) 결과를 로컬에서 확인하는 스크립트.

test_field_questions.py는 generate_question()만 테스트해서 임베딩 유사도 검증 + 코퍼스
폴백 단계를 안 거친 결과를 보여준다 - 이 스크립트는 그 검증까지 다 거친, 실제 사용자에게
나가는 것과 동일한 결과를 보여준다.

사용법(ai-server 폴더에서, venv 활성화한 상태):
    python ml/test_field_questions_validated.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview.question_generator import generate_validated_question  # noqa: E402

FIELDS = ["백엔드", "프론트엔드", "풀스택", "모바일 (iOS/Android)", "데이터 · AI · 기타"]

print("모델 로딩 중... (첫 실행은 몇십 초~몇 분 걸릴 수 있음)")

for field in FIELDS:
    print(f"\n[{field}]")
    for _ in range(2):
        q = generate_validated_question(job=field, category="기술_직무역량")
        print(" -", q)

# 인성/역량 계열은 분야를 안 타야 정상 - 그것도 같이 확인
print("\n[공통 - 협업_리더십_커뮤니케이션, 분야 무관해야 정상]")
print(" -", generate_validated_question(job="ICT 개발자(신입)", category="협업_리더십_커뮤니케이션"))
