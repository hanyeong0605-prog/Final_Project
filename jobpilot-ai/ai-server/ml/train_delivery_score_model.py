"""build_delivery_score_dataset.py가 만든 CSV로 답변 전달력(delivery_score) 예측 회귀모델을
학습한다.

입력 피처는 audio_analysis.analyze_voice()가 실제 서비스에서도 뽑는 것과 완전히 같은
8개(말속도/피치평균/피치변동폭/침묵비율/긴침묵횟수/음량평균/음량변동폭/답변길이) - 그래서
학습된 모델을 실서비스 요청에 그대로 적용할 수 있다.

2026-08-13: 표본이 적을 때(수십~수백 건) 단일 train_test_split 20%(예: 47건 중 10건)로
낸 R²는 어느 데이터가 테스트셋으로 빠지느냐에 따라 크게 흔들려서 신뢰하기 어렵다
(실제로 47건 기준 R² -0.33이 나왔는데, 이게 "이 방식 자체가 안 통한다"는 뜻인지
"표본이 적어서 운이 나빴다"는 뜻인지 구분이 안 됐음). K-겹 교차검증으로 데이터 전체를
번갈아 검증셋으로 써서 더 안정적인 성능 추정치를 내고, 후보 모델 여러 개(선형 베이스라인
Ridge, RandomForest, GradientBoosting)를 같은 기준으로 비교해서 그중 가장 나은 것만
채택한다 - "일단 RandomForest로 정했다"가 아니라 실제로 비교해서 고른다.

실서비스 반영 기준(_MIN_CV_R2): 교차검증 평균 R²가 이 값 미만이면 "아직 실서비스에 쓰기엔
부족하다"고 판단해서 app/domain/interview/model/(실제 evaluation 파이프라인이 로드하는
경로)에는 저장하지 않고, ml/ 아래 실험용 경로에만 남긴다 - 성능 미달 모델이 조용히
실서비스 채점 로직에 올라가는 걸 막기 위한 안전장치. (2026-08-13 기준 evaluation.py는 아직
이 모델을 전혀 로드하지 않고 delivery_score도 세션 평가 Gemini 호출에 포함해서 받는다 -
이 모델은 별도로 만들어두는 실험/보조 모델이고, 실제로 evaluation 경로에 연결하려면
별도 배선 작업이 필요하다.)

사용법(ai-server 폴더에서, venv 활성화한 상태, build_delivery_score_dataset.py를 먼저
돌려서 ml/delivery_score_dataset.csv가 있어야 함):
    python ml/train_delivery_score_model.py
"""

import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import KFold, cross_val_predict

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

DATASET_CSV = Path(__file__).parent / "delivery_score_dataset.csv"
MODEL_DIR = Path(__file__).resolve().parent.parent / "app" / "domain" / "interview" / "model"
MODEL_PATH = MODEL_DIR / "delivery_score_model.joblib"
# 성능 기준(_MIN_CV_R2) 미달이어도 실험 기록은 남겨서, 다음에 데이터를 더 모아 재학습할 때
# "지난번엔 몇 점이었는지" 비교할 수 있게 한다.
EXPERIMENTAL_MODEL_PATH = Path(__file__).parent / "delivery_score_model_experimental.joblib"

FEATURE_COLUMNS = [
    "duration_sec",
    "speaking_rate_chars_per_min",
    "pitch_mean_hz",
    "pitch_variation_hz",
    "silence_ratio",
    "long_pause_count",
    "volume_mean_rms",
    "volume_variation_rms",
]
TARGET_COLUMN = "delivery_score"

# 표본이 적을 때(예: --limit로 30건만 테스트) 학습/평가 나누기 자체가 무의미해지는 것을
# 막는 최소 기준 - 이 밑이면 우선 더 많이 모으라고 안내만 하고 종료한다.
_MIN_SAMPLES = 30

# 2026-08-13: 교차검증 평균 R²가 이 값 미만이면 실서비스 경로(MODEL_PATH)에 승격하지 않는다.
# 0.3은 느슨한 기준이다(피처 8개짜리 표 데이터 + 다소 잡음 섞인 라벨 특성상 0.5+ 는
# 데이터가 훨씬 많이 쌓이기 전엔 기대하기 어려움) - "쓸만한 보조 신호" 정도의 최소선이라고
# 보면 된다. 데이터가 늘어나면서 이 기준을 점진적으로 올려도 된다.
_MIN_CV_R2 = 0.3


def _cv_splits_for(n_samples: int) -> int:
    # 5-겹이 기본이지만, 표본이 아주 적을 땐(30~50건) 폴드당 표본이 너무 줄어드니 최소
    # 5건/폴드는 보장되게 겹 수를 줄인다.
    return max(2, min(5, n_samples // 5))


def _evaluate_candidate(name: str, model, X: pd.DataFrame, y: pd.Series, cv: KFold) -> dict:
    """cross_val_predict로 "각 샘플이 검증셋일 때의 예측값"을 전부 모아서 MAE/R²를 계산한다
    - cross_val_score를 폴드별로 평균만 내는 것보다, 표본이 적을 때 지표가 덜 흔들린다."""
    pred = cross_val_predict(model, X, y, cv=cv)
    mae = mean_absolute_error(y, pred)
    r2 = r2_score(y, pred)
    return {"name": name, "model": model, "mae": mae, "r2": r2}


def main() -> None:
    if not DATASET_CSV.exists():
        raise SystemExit(
            f"{DATASET_CSV}가 없습니다. 먼저 python ml/build_delivery_score_dataset.py 를 실행해서 "
            "데이터를 만들어야 합니다."
        )

    df = pd.read_csv(DATASET_CSV)
    # pitch_mean_hz/pitch_variation_hz는 답변 전체가 무성음이었으면 None(빈 값)일 수 있다
    # (audio_analysis.py _pitch_stats 참고) - 회귀모델은 결측치를 못 받으므로 그 행은 뺀다.
    before = len(df)
    df = df.dropna(subset=FEATURE_COLUMNS + [TARGET_COLUMN])
    dropped = before - len(df)
    if dropped:
        print(f"결측치 있는 {dropped}건 제외 (피치 추출 실패한 무성음 답변 등)")

    print(f"학습 가능한 샘플 수: {len(df)}건")
    if len(df) < _MIN_SAMPLES:
        raise SystemExit(
            f"샘플이 {len(df)}건뿐이라 학습을 건너뜁니다(최소 {_MIN_SAMPLES}건 권장). "
            "build_delivery_score_dataset.py를 --limit 없이(또는 더 큰 값으로) 더 돌려주세요."
        )

    X = df[FEATURE_COLUMNS]
    y = df[TARGET_COLUMN]

    n_splits = _cv_splits_for(len(df))
    cv = KFold(n_splits=n_splits, shuffle=True, random_state=42)

    candidates = [
        # 선형 베이스라인 - 비선형 모델들이 "그냥 평균 근처로 찍는 것"보다는 나은지 비교하는
        # 기준선. 이것보다 못한 모델은 채택할 이유가 없다.
        ("Ridge(선형 베이스라인)", Ridge(alpha=1.0)),
        ("RandomForest", RandomForestRegressor(n_estimators=200, max_depth=8, random_state=42, n_jobs=-1)),
        (
            "GradientBoosting",
            GradientBoostingRegressor(n_estimators=200, max_depth=3, learning_rate=0.05, random_state=42),
        ),
    ]

    print(f"\n{n_splits}-겹 교차검증으로 후보 {len(candidates)}개 비교 중...")
    results = [_evaluate_candidate(name, model, X, y, cv) for name, model in candidates]
    results.sort(key=lambda r: r["r2"], reverse=True)

    print("\n후보 비교 결과 (교차검증 기준 - 전체 데이터를 번갈아 검증셋으로 사용):")
    for r in results:
        print(f"  {r['name']:<24} MAE {r['mae']:5.2f}점   R² {r['r2']:+.3f}")

    best = results[0]
    print(f"\n채택: {best['name']} (R² {best['r2']:+.3f}, MAE {best['mae']:.2f}점)")

    # 최종 모델은 교차검증에 쓴 것과 같은 설정으로, 데이터 전체에 다시 학습(refit)한다 -
    # 교차검증은 "이 설정이 얼마나 잘 통하는지"를 추정하기 위한 것이고, 실제로 저장해서
    # 쓸 모델은 가진 데이터를 하나도 버리지 않고 전부 학습에 쓰는 게 낫다.
    final_model = best["model"]
    final_model.fit(X, y)

    if hasattr(final_model, "feature_importances_"):
        importances = sorted(zip(FEATURE_COLUMNS, final_model.feature_importances_), key=lambda x: -x[1])
        print("\n피처 중요도:")
        for name, importance in importances:
            print(f"  {name}: {importance:.3f}")
    elif hasattr(final_model, "coef_"):
        # Ridge는 스케일이 다른 피처를 그대로 넣으면 계수 크기만으로 중요도를 비교할 수
        # 없어서(Hz vs 비율 vs 초), 표준화한 계수 기준으로 별도 참고치만 보여준다.
        std = X.std().replace(0, np.nan)
        standardized = sorted(
            zip(FEATURE_COLUMNS, (final_model.coef_ * std).fillna(0)), key=lambda x: -abs(x[1])
        )
        print("\n피처 영향력(표준화 계수, 참고용):")
        for name, coef in standardized:
            print(f"  {name}: {coef:+.3f}")

    artifact = {
        "model": final_model,
        "model_name": best["name"],
        "feature_columns": FEATURE_COLUMNS,
        "cv_r2": best["r2"],
        "cv_mae": best["mae"],
        "n_samples": len(df),
    }

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    if best["r2"] >= _MIN_CV_R2:
        joblib.dump(artifact, MODEL_PATH)
        print(f"\n[실서비스 반영 가능] 기준(R² ≥ {_MIN_CV_R2}) 통과 - 저장 완료: {MODEL_PATH}")
        print("주의: evaluation.py는 아직 이 파일을 자동으로 로드하지 않는다 - 실제로 서비스에 " "붙이려면 evaluation.py 쪽 배선이 별도로 필요하다.")
    else:
        joblib.dump(artifact, EXPERIMENTAL_MODEL_PATH)
        print(
            f"\n[실서비스 반영 보류] R² {best['r2']:+.3f}가 기준(≥ {_MIN_CV_R2}) 미달이라 "
            f"{MODEL_PATH.name}(실서비스 경로)에는 저장하지 않았다. 실험 결과만 남김: "
            f"{EXPERIMENTAL_MODEL_PATH}"
        )
        print("데이터를 더 모아서(ml/build_delivery_score_dataset.py) 다시 돌려보세요.")


if __name__ == "__main__":
    main()
