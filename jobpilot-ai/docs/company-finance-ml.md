# DART 기업 성장 가능성 ML

이 모델은 DART 재무제표의 공개된 과거 사실로 다음 사업연도의 재무 방향을 추정한다.
미래 실적 보장이나 투자 조언이 아니며, 홀드아웃 평가 기준을 통과한 아티팩트만 서비스한다.

## 데이터와 누수 방지

- 한 행은 `corp_code + base_year`다.
- 피처는 기준연도 `t`까지의 연속 3개년(`t-2`, `t-1`, `t`)만 사용한다.
- 정답은 다음 해 `t+1`의 매출 성장, 수익성 개선, 안정성 위험이다.
- 네 연도 중 하나라도 빠지거나 필수 계정·분모가 없으면 행을 제외하고 임의 보간하지 않는다.
- 학습/검증은 회사 행을 무작위로 섞지 않고 `base_year` 컷오프로 분리한다.
- 운영 수집은 기본적으로 완료된 최근 7개 사업연도를 가져온다. `DART_FINANCIAL_YEARS_BACK`은
  최소 4 이상이어야 하며, 학습·홀드아웃 연도를 모두 확보하려면 기본값 7을 유지한다.

## 실행

AI 서버 디렉터리에서 다음을 실행한다. 원본 CSV와 모델 파일은 Git에 커밋하지 않는다.

```powershell
.\.venv\Scripts\python.exe -m ml.company_finance_dataset --output ml/data/company-finance/company-years.csv
.\.venv\Scripts\python.exe -m ml.train_company_growth_model --dataset ml/data/company-finance/company-years.csv --cutoff-year 2023 --output ml/artifacts/company-growth-v1
```

`metadata.json`에는 학습/홀드아웃 연도, 행 수, 피처 목록, 매출 성장률 MAE, 세 분류의 F1과 Brier score,
scikit-learn 버전, 검증 통과 여부가 저장된다. 기본 통과 기준은 MAE 0.35 이하, 각 F1 0.50 이상,
각 Brier score 0.30 이하이다. 실제 표본 분포를 확인한 후 기준 변경 시 모델 버전도 변경한다.

## 서비스

검증을 통과한 아티팩트만 `COMPANY_GROWTH_MODEL_DIR`에 읽기 전용으로 연결한다.
내부 API `POST /company-finance/predict`는 `X-Internal-API-Key` 인증과 완전한 피처 스냅샷을 요구한다.
모델 부재·손상·검증 실패 시 503을 반환하며 Spring은 기존 `FINANCIALS_ONLY` 상태를 유지해야 한다.
