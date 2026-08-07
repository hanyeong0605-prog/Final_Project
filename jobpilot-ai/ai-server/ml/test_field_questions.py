"""재학습된 LoRA 모델이 분야별로 실제로 다른 질문을 내는지 로컬에서 빠르게 확인하는 스크립트.

Colab 노트북 8단계(테스트 셀)와 똑같은 걸 로컬 venv에서 돌려보는 용도 - 이미
ai-server/app/domain/interview/model/question_generator_lora/에 새 어댑터를 덮어썼다는
전제. GPU 없어도 CPU로 동작한다(느릴 뿐 에러는 안 남).

사용법(ai-server 폴더에서, venv 활성화한 상태):
    python ml/test_field_questions.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview.question_generator import generate_question  # noqa: E402

FIELDS = ["백엔드", "프론트엔드", "풀스택", "모바일 (iOS/Android)", "데이터 · AI · 기타"]

print("모델 로딩 중... (첫 실행은 몇십 초~몇 분 걸릴 수 있음)")

for field in FIELDS:
    print(f"\n[{field}]")
    for _ in range(2):
        q = generate_question(job=field, category="기술_직무역량")
        print(" -", q)

# 인성/역량 계열은 분야를 안 타야 정상 - 그것도 같이 확인
print("\n[공통 - 협업_리더십_커뮤니케이션, 분야 무관해야 정상]")
print(" -", generate_question(job="ICT 개발자(신입)", category="협업_리더십_커뮤니케이션"))
