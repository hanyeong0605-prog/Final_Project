"""LoRA 질문 생성 모델이 실제로 몇 개 정도의 "서로 다른" 질문을 만들어낼 수 있는지
가늠해보는 스크립트. 결제 시 "중복 질문 안 나오게" 정책을 정하기 전에, 모델의 실질적인
다양성 한계를 먼저 숫자로 확인하려는 목적.

Gemini 검수(_gemini_polish)는 안 거친다 - 검수는 문장을 다듬거나 버릴 뿐 다양성을
"늘려주지"는 못하니까(로컬 모델이 뽑아내는 후보 수가 다양성의 진짜 상한선), API 비용 없이
로컬 모델만으로 상한을 먼저 재는 게 맞다.

사용법:
  python ml/debug_question_diversity.py [생성 개수(기본 60)]
"""

import sys
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.domain.interview.question_generator import (  # noqa: E402
    DEFAULT_JOB,
    _cut_at_first_ending,
    _get_loaded_model,
    _looks_like_question,
)

NEAR_DUPLICATE_THRESHOLD = 0.8  # SequenceMatcher ratio 기준 - 이 이상이면 "사실상 같은 질문"으로 취급


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 60

    print(f"모델 로딩 중... (처음 한 번만 몇 초~수십 초 걸림)")
    loaded = _get_loaded_model()
    tokenizer, model = loaded.tokenizer, loaded.model

    prompt = f"직무: {DEFAULT_JOB}\n이전 답변: \n다음 질문:"
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

    import torch

    print(f"질문 {n}개 생성 중 (한 번의 generate() 호출)...")
    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_new_tokens=40,
            do_sample=True,
            top_p=0.85,
            temperature=0.7,
            repetition_penalty=1.3,
            no_repeat_ngram_size=3,
            pad_token_id=tokenizer.pad_token_id,
            num_return_sequences=n,
        )

    candidates = []
    for sequence in output:
        text = tokenizer.decode(sequence, skip_special_tokens=True)
        candidate = text.split("다음 질문:")[-1].strip()
        candidate = _cut_at_first_ending(candidate)
        if candidate:
            candidates.append(candidate)

    valid = [c for c in candidates if _looks_like_question(c)]

    print(f"\n생성 {len(candidates)}개 중 형식 통과(질문처럼 끝남) {len(valid)}개\n")

    # 1) 완전 동일 문자열 기준 중복
    counts = Counter(valid)
    exact_unique = len(counts)
    print(f"=== 완전 일치 기준 ===")
    print(f"고유 문장 수: {exact_unique} / {len(valid)}")
    dupes = {q: c for q, c in counts.items() if c > 1}
    if dupes:
        print("완전히 똑같이 반복된 질문:")
        for q, c in sorted(dupes.items(), key=lambda x: -x[1]):
            print(f"  ({c}회) {q}")

    # 2) 근사 중복(표현만 살짝 다른 것) - 모든 쌍을 비교(개수 적어서 O(n^2)로 충분)
    unique_list = list(counts.keys())
    clusters: list[list[str]] = []
    used = [False] * len(unique_list)
    for i in range(len(unique_list)):
        if used[i]:
            continue
        cluster = [unique_list[i]]
        used[i] = True
        for j in range(i + 1, len(unique_list)):
            if used[j]:
                continue
            ratio = SequenceMatcher(None, unique_list[i], unique_list[j]).ratio()
            if ratio >= NEAR_DUPLICATE_THRESHOLD:
                cluster.append(unique_list[j])
                used[j] = True
        clusters.append(cluster)

    print(f"\n=== 근사 중복(유사도 {NEAR_DUPLICATE_THRESHOLD} 이상) 묶음 기준 ===")
    print(f"실질적으로 서로 다른 질문 '군집' 수: {len(clusters)} / {len(valid)}")
    near_dupe_clusters = [c for c in clusters if len(c) > 1]
    if near_dupe_clusters:
        print("비슷한 질문끼리 묶인 것들:")
        for cluster in near_dupe_clusters:
            print(f"  - {cluster[0]}")
            for other in cluster[1:]:
                print(f"      ~= {other}")

    print("\n=== 생성된 전체 목록 ===")
    for i, c in enumerate(valid, 1):
        print(f"[{i}] {c}")


if __name__ == "__main__":
    main()
