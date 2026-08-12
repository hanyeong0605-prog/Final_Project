"""build_delivery_score_dataset.py가 만든 CSV로 답변 전달력(delivery_score) 예측 회귀모델을
학습한다.

입력 피처는 audio_analysis.analyze_voice()가 실제 서비스에서도 뽑는 것과 완전히 같은
8개(말속도/피치평균/피치변동폭/침묵비율/긴침묵횟수/음량평균/음량변동폭/답변길이) - 그래서
학습된 모델을 실서비스 요청에 그대로 적용할 수 있다.

RandomForestRegressor를 쓴 이유: 피처 스케일이 서로 많이 달라도(Hz vs 비율 vs 초) 별도
정규화 없이 잘 동작하고, 피처 몇 개 안 되는 표 형태 데이터에서 선형회귀보다 비선형 관계를
잘 잡는다 - 그러면서도 학습이 몇 초 안에 끝날 만큼 가볍다.

사용법(ai-server 폴더에서, venv 활성화한 상태, build_delivery_score_dataset.py를 먼저
돌려서 ml/delivery_score_dataset.csv가 있어야 함):
    python ml/train_delivery_score_model.py
"""

import sys
from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

DATASET_CSV = Path(__file__).parent / "delivery_score_dataset.csv"
MODEL_DIR = Path(__file__).resolve().parent.parent / "app" / "domain" / "interview" / "model"
MODEL_PATH = MODEL_DIR / "delivery_score_model.joblib"

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
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    model = RandomForestRegressor(n_estimators=200, max_depth=8, random_state=42, n_jobs=-1)
    model.fit(X_train, y_train)

    pred = model.predict(X_test)
    mae = mean_absolute_error(y_test, pred)
    r2 = r2_score(y_test, pred)
    print(f"\n검증 결과 (테스트셋 {len(X_test)}건 기준)")
    print(f"  MAE(평균 절대 오차): {mae:.2f}점  - Gemini가 매긴 점수랑 평균 이 정도 차이남")
    print(f"  R²: {r2:.3f}  - 1에 가까울수록 피처로 점수 변화를 잘 설명한다는 뜻")

    # 어떤 피처가 예측에 가장 크게 기여했는지 - 발표 자료에 쓰기 좋다("말속도가 제일 중요한
    # 신호였다" 같은 실제 근거를 댈 수 있음).
    importances = sorted(zip(FEATURE_COLUMNS, model.feature_importances_), key=lambda x: -x[1])
    print("\n피처 중요도:")
    for name, importance in importances:
        print(f"  {name}: {importance:.3f}")

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "feature_columns": FEATURE_COLUMNS}, MODEL_PATH)
    print(f"\n모델 저장 완료: {MODEL_PATH}")


if __name__ == "__main__":
    main()
