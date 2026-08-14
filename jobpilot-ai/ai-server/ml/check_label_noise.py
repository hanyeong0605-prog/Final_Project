"""delivery_score 라벨(Gemini 채점)이 얼마나 일관적인지 진단한다.

배경: train_delivery_score_model.py로 486건까지 학습시켜봐도 R²가 +0.076에 그쳤다.
데이터를 더 모아도 개선이 크지 않다면(build_delivery_score_dataset.py 결과 참고),
"모델/데이터가 부족해서"가 아니라 "애초에 라벨(Gemini의 채점)이 같은 답변에도
들쭉날쭉해서" 일 수도 있다 - 이 경우 아무리 좋은 모델을 써도 그 노이즈 이상으로는
정확해질 수 없다(통계학의 신뢰도 상한 개념, test-retest reliability와 동일).

이 스크립트는:
    1. 이미 채점된 delivery_score_dataset.csv에서 표본 N개를 뽑는다.
    2. 같은 샘플(같은 transcript + voice_metrics)을 Gemini에게 K번씩 다시 채점시킨다.
    3. 샘플별 반복 채점의 분산(노이즈)을 구하고, 전체 데이터셋의 분산과 비교해서
       "이론상 최고 R²"를 추정한다: R²_ceiling ≈ 1 - (평균 노이즈 분산 / 전체 라벨 분산)
       (신뢰도 계수와 같은 공식 - 노이즈가 클수록 R² 상한이 낮아진다.)

사용법(ai-server 폴더에서, venv 활성화):
    python ml/check_label_noise.py --samples 30 --repeats 3

주의: 샘플 30개 x 반복 3번 = 약 90번 Gemini 호출. 분당 한도(15회/분) 감안하면
대략 6~7분 걸린다. 토큰이 부족하면 --samples를 줄여서(예: 10개) 먼저 감을 잡아도 된다.
"""

import argparse
import csv
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# build_delivery_score_dataset.py의 채점 함수/설정을 그대로 재사용한다 - 같은 프롬프트로
# 채점해야 "라벨 자체의 노이즈"를 재는 것이지, 프롬프트 차이로 인한 노이즈가 섞이지 않는다.
from ml.build_delivery_score_dataset import (  # noqa: E402
    DEFAULT_LABELS_DIR,
    _FEATURE_COLUMNS,
    _score_delivery,
    _SLEEP_BETWEEN_CALLS_SEC,
    OUTPUT_CSV,
)
from app.core.config import settings  # noqa: E402


def _load_transcript(labels_dir: Path, sample_id: str) -> str | None:
    import json

    label_path = labels_dir / f"{sample_id}.json"
    if not label_path.exists():
        return None
    try:
        data = json.loads(label_path.read_text(encoding="utf-8"))
        return data["dataSet"]["answer"]["raw"]["text"]
    except (KeyError, ValueError):
        return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--labels-dir", type=Path, default=DEFAULT_LABELS_DIR)
    parser.add_argument("--dataset-csv", type=Path, default=OUTPUT_CSV)
    parser.add_argument("--samples", type=int, default=30, help="재채점할 샘플 개수")
    parser.add_argument("--repeats", type=int, default=3, help="샘플당 반복 채점 횟수")
    args = parser.parse_args()

    if not settings.gemini_api_key:
        raise SystemExit("GEMINI_API_KEY가 설정돼 있지 않습니다 (.env 확인).")
    from google import genai

    client = genai.Client(api_key=settings.gemini_api_key)

    with args.dataset_csv.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise SystemExit(f"{args.dataset_csv}에 데이터가 없습니다.")

    # 앞에서부터 N개 - 무작위로 뽑아도 되지만, 재현 가능하게 순서대로 고정한다.
    sample_rows = rows[: args.samples]
    original_scores = [float(r["delivery_score"]) for r in rows]
    total_variance = statistics.pvariance(original_scores)

    print(f"전체 데이터셋 {len(rows)}건, delivery_score 분산: {total_variance:.2f}")
    print(f"{len(sample_rows)}개 샘플을 {args.repeats}번씩 재채점합니다 (원래 채점 1회 포함 총 {args.repeats+1}회 비교)...\n")

    per_sample_variances: list[float] = []
    per_sample_ranges: list[tuple[str, list[float]]] = []

    # 2026-08-13: 무료 티어는 분당 한도(15회)뿐 아니라 하루 한도(500회)도 있다 - 하루치를
    # 다 쓰면 _score_delivery가 재시도(65초 대기)를 다 소진한 뒤 결국 예외를 던진다. 이때
    # 스크립트가 통째로 죽어서 그때까지 모은 결과가 전부 날아가면 안 되므로, 여기서 잡아서
    # 지금까지 모은 것만이라도 분석/출력하고 깔끔하게 멈춘다.
    quota_exhausted = False
    for i, row in enumerate(sample_rows, start=1):
        if quota_exhausted:
            break
        sample_id = row["sample_id"]
        transcript = _load_transcript(args.labels_dir, sample_id)
        if transcript is None:
            print(f"  [스킵] {sample_id}: 원본 라벨 json을 못 찾음")
            continue

        voice_metrics = {col: float(row[col]) for col in _FEATURE_COLUMNS}
        scores = [float(row["delivery_score"])]  # 원래 채점값도 비교에 포함

        for attempt in range(args.repeats):
            try:
                score = _score_delivery(client, transcript, voice_metrics)
            except Exception as exc:
                print(f"\n  [중단] {sample_id} 재채점 중 실패: {type(exc).__name__}: {exc}")
                print("  API 한도(하루 한도 포함)에 걸린 것으로 보입니다 - 지금까지 모은 결과로 분석을 마칩니다.\n")
                quota_exhausted = True
                break
            if score is not None:
                scores.append(float(score))
            time.sleep(_SLEEP_BETWEEN_CALLS_SEC)

        if len(scores) < 2:
            continue

        variance = statistics.pvariance(scores)
        per_sample_variances.append(variance)
        per_sample_ranges.append((sample_id, scores))
        print(f"  [{i}/{len(sample_rows)}] {sample_id}: {scores} (분산 {variance:.1f})")

    if not per_sample_variances:
        raise SystemExit("재채점된 샘플이 없어 분석할 수 없습니다. 나중에(한도 리셋 후) 다시 시도해주세요.")

    if quota_exhausted:
        print(f"(참고: 한도 때문에 {len(sample_rows)}개 중 {len(per_sample_variances)}개만 완료된 결과입니다)")

    avg_noise_variance = statistics.mean(per_sample_variances)
    ceiling_r2 = max(0.0, 1 - (avg_noise_variance / total_variance)) if total_variance > 0 else 0.0

    print("\n" + "=" * 60)
    print(f"샘플별 평균 노이즈 분산: {avg_noise_variance:.2f} (표준편차 {avg_noise_variance ** 0.5:.2f}점)")
    print(f"전체 라벨 분산: {total_variance:.2f}")
    print(f"이론상 최고 R² (신뢰도 상한): {ceiling_r2:.3f}")
    print("=" * 60)
    if ceiling_r2 < 0.3:
        print(
            "\n=> 라벨 자체가 너무 들쭉날쭉해서, 어떤 모델을 써도 R² 0.3을 넘기기 "
            "현실적으로 어렵습니다. 데이터를 더 모으기보다 프롬프트를 더 구체적으로 "
            "만들거나(채점 기준 세분화), 여러 번 채점해서 평균낸 값을 라벨로 쓰는 "
            "방식을 고려해야 합니다."
        )
    else:
        print(
            f"\n=> 이론상 R² {ceiling_r2:.3f}까지는 가능하다는 뜻이므로, 지금 모델(R² 0.076)은 "
            "아직 그 상한에 한참 못 미칩니다 - 데이터를 더 모으거나 피처를 추가하면 "
            "개선 여지가 있습니다."
        )


if __name__ == "__main__":
    main()
