"""기존 AI Hub 질문 5039개(interview_qa_pairs.jsonl 앞부분)를 Gemini로 분류/정제하기
위한 배치 프롬프트 파일들을 만든다. 팀 공용 API 토큰을 안 쓰고 본인 Gemini 앱/웹
채팅에 직접 붙여넣는 용도라서, API 호출은 하지 않고 그냥 텍스트 파일만 만든다.

사용법:
  python ml/build_classification_batches.py

출력: ml/classification_batches/batch_001.txt, batch_002.txt, ... 로 저장됨.
각 파일을 순서대로 열어서 안의 프롬프트 전체를 복사 -> Gemini에 붙여넣기 -> 결과를
받아서 ml/ai_hub_tagged.jsonl 에 이어 붙이면 된다(파일 하나 만들어서 계속 append).
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE_FILE = ROOT / "interview_qa_pairs.jsonl"
OUTPUT_DIR = ROOT / "classification_batches"
BATCH_SIZE = 250
# 이 스크립트를 처음 돌리는 시점 기준 AI Hub 원본 줄 수. Gemini로 이미 추가한 뒷부분
# (gemini_added_tagged.jsonl로 이미 따로 처리함)은 다시 분류할 필요 없어서 앞부분만 자른다.
AI_HUB_COUNT = 5039

CATEGORY_GUIDE = """- 자기소개_지원동기: 자기소개, 지원 동기, 회사 관심, 입사 후 포부/비전, 마무리 질문
- 강점_약점: 본인의 장점/단점/성향에 대한 질문
- 협업_리더십_커뮤니케이션: 협업, 갈등 해결, 리더십, 소통 방식, 조직문화 적합성
- 문제해결_도전경험: 문제해결 경험, 실패/좌절 극복, 창의성/개선 경험, 새로운 환경 적응
- 기술_직무역량: 프로그래밍/CS 기초/백엔드/프론트엔드/네트워크/Git 등 기술 지식이나 직무 전문성
- 가치관_자기관리: 윤리/가치관 판단, 스트레스·압박 대처, 시간·우선순위 관리, 학습 태도"""


def build_prompt(items: list[dict]) -> str:
    numbered = "\n".join(f"{i+1}. {item['question']}" for i, item in enumerate(items))
    return f"""너는 한국 채용면접 질문 데이터를 정리하는 중이야. 아래 번호가 매겨진 질문
목록 각각에 대해 다음을 수행해줘.

1. 아래 6개 카테고리 중 하나로 분류해라(카테고리 키를 정확히 그대로 써야 함):
{CATEGORY_GUIDE}

2. 문장이 문법적으로 어색하거나, 존재하지 않는 단어가 섞여 있거나, 질문 두 개가 어색하게
   이어붙어 있거나, 질문이 아니라 서술문으로 끝나면 자연스럽게 고쳐라 - 다루는 주제/내용
   자체는 절대 바꾸지 마라.
3. 지원자를 가리킬 땐 "지원자님"/"지원자분"으로 통일해라 - "면접자" 같은 다른 호칭이 있으면
   고쳐라.
4. 위 방법으로도 고칠 수 없을 만큼 내용이 망가졌거나 의미가 안 통하면, 그 번호는 결과에서
   통째로 빼라(출력하지 마라) - 억지로 살리지 마라.
5. 같은 의미의 질문이 이 목록 안에 중복으로 여러 개 있으면(표현만 다르고 사실상 같은 질문),
   그중 하나만 남기고 나머지는 빼라.

출력은 아래 JSON Lines 형식으로만, 설명·번호·마크다운 없이 한 줄에 하나씩만:
{{"question": "질문 텍스트(수정했으면 수정된 버전)", "category": "카테고리_키"}}

질문 목록:
{numbered}
"""


def main():
    lines = SOURCE_FILE.read_text(encoding="utf-8").splitlines()
    ai_hub = lines[:AI_HUB_COUNT]
    items = [json.loads(l) for l in ai_hub]

    OUTPUT_DIR.mkdir(exist_ok=True)
    total_batches = (len(items) + BATCH_SIZE - 1) // BATCH_SIZE
    for batch_idx in range(total_batches):
        chunk = items[batch_idx * BATCH_SIZE : (batch_idx + 1) * BATCH_SIZE]
        prompt = build_prompt(chunk)
        out_path = OUTPUT_DIR / f"batch_{batch_idx + 1:03d}.txt"
        out_path.write_text(prompt, encoding="utf-8")

    print(f"{len(items)}개를 {total_batches}개 배치({BATCH_SIZE}개씩)로 나눠서")
    print(f"{OUTPUT_DIR}/batch_001.txt ~ batch_{total_batches:03d}.txt 에 저장했다.")
    print("각 파일 내용을 순서대로 복사해서 Gemini에 붙여넣고, 결과를")
    print("ml/ai_hub_tagged.jsonl 에 이어서 붙여넣으면 된다.")


if __name__ == "__main__":
    main()
