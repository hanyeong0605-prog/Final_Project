# 기업 재무 성장 가능성 ML — 학원 인수인계

> 작성일: 2026-08-31  
> 대상 브랜치: `김한영브뤤취학원` → PR → `main`  
> 목적: 채용공고 회사의 재무제표를 수집하고, 검증된 ML 모델로 **성장 가능성** 지표를 제공한다.

## 1. 현재 기능과 데이터 흐름

서비스 화면의 채용공고 상세에서 `기업 재무 분석` 영역을 제공한다.

```text
채용공고의 회사명
  → OpenDART 법인 마스터 이름 매칭
  → DART 고유번호(corp_code)
  → DART 재무제표 수집
  → (DART에 없는 연도만) 공공데이터포털 요약 재무제표 보강
  → company_financial_years 테이블
  → AI 서버가 학습용 회사-연도 행 생성
  → 랜덤 포레스트 모델 학습·검증
  → company_growth_predictions 테이블
  → 채용공고 상세의 성장 가능성 카드
```

### ML이 예측하는 것

입력은 이전 연도의 매출액, 영업이익, 순이익, 총자산, 부채, 자본 및 파생비율이다.

- 매출 성장 가능성
- 수익성 개선 가능성
- 재무 위험 신호(부채비율·변화율·순이익으로 계산하는 규칙 기반 지표이며 ML 확률이 아님)

이는 미래를 확정하는 정보가 아니라, 과거 재무 패턴을 기반으로 한 **성장 가능성 예측 지표**다.

## 2. 현재 확인된 상태

- DART 이름 매칭 결과(기존 전체 실행 기준)
  - 전체 회사명: 1,627개
  - 확정 매칭: 520개
  - 후보 검토 필요: 891개
  - 미매칭: 216개
- 현재 학습 데이터셋: 157개 회사-연도 행 / 46개 기업
- 현재 ML 예측 저장: 49개 기업
- DART 재무 동기화는 정상 동작한다.
- 공공데이터포털 보강 결과가 `0건`인 문제를 진단 중이다.

공공 보강 실패의 원인을 숨기지 않도록 다음 로그를 배포 워크플로에서 출력하도록 수정되어 있다.

```text
Public finance fallback diagnostics:
  confirmedCorporations=...
  registrationResolved=...
  missingDartYears=...
  apiRecords=...
  noPublicRecord=...
  apiFailures=...
  firstApiFailure=...
  storedAnnualStatements=...
```

각 숫자의 의미는 다음과 같다.

| 항목 | 의미 |
| --- | --- |
| `confirmedCorporations` | DART 이름 매칭이 확정된 기업 수 |
| `registrationResolved` | DART `company.json`에서 법인등록번호(`jurir_no`)까지 확보한 기업 수 |
| `missingDartYears` | DART 연간 재무가 없는 기업-연도 조합 수 |
| `apiRecords` | 공공데이터 API가 실제 재무 행을 반환한 수 |
| `noPublicRecord` | API 요청은 정상이지만 해당 법인등록번호·연도에 재무 행이 없는 수 |
| `apiFailures` | API 인증·응답 형식·HTTP 요청 오류 수 |
| `firstApiFailure` | 첫 API 오류 코드와 안전한 요약 메시지(키·요청 URL은 출력하지 않음) |
| `storedAnnualStatements` | DB에 최종 저장된 공공 재무제표 수 |

## 3. 필수 환경변수

운영 서버 파일: `/etc/jobpilot/jobpilot.env`

```dotenv
# 기존 OpenDART 인증키
DART_API_KEY=<OpenDART API key>

# 공공데이터포털 '기업 재무정보' 서비스키
DATA_GO_KR_SERVICE_KEY=<data.go.kr service key>

# 백엔드와 AI 서버가 예측 API를 인증할 때 공통으로 사용하는 내부 키
INTERNAL_API_KEY=<random shared internal key>

# 기본값을 유지해도 된다.
DATA_GO_KR_FINANCE_BASE_URL=http://apis.data.go.kr/1160100/service/GetFinaStatInfoService_V2
```

주의 사항:

- 키 값 자체를 Git, PR, 채팅, Markdown에 기록하지 않는다.
- 공공데이터포털은 인코딩 키와 디코딩 키를 모두 보여줄 수 있다. 구현은 두 형태를 받아 내부에서 한 번만 URL 인코딩하도록 되어 있다.
- 키를 새로 발급하거나 수정하면 서버 환경파일 저장 후 재배포해야 한다.

## 4. 핵심 파일

| 역할 | 파일 |
| --- | --- |
| 배포 및 수동 실행 | `.github/workflows/deploy.yml` |
| DART 회사 매칭 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyDartBackfillService.java` |
| DART 재무 수집 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyFinancialSyncService.java` |
| 공공 재무 보강 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/PublicCompanyFinancialSyncService.java` |
| 공공 API HTTP 호출 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/client/PublicCompanyFinancialClient.java` |
| 공공 API JSON 파싱 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/client/PublicCompanyFinancialParser.java` |
| 시작 시 수집 순서 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/DartCompanyFinanceBackfillRunner.java` |
| 학습 데이터 생성 | `ai-server/ml/company_finance_dataset.py` |
| 모델 학습 | `ai-server/ml/train_company_growth_model.py` |
| 예측 저장 | `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyGrowthPredictionRunner.java` |
| DB 마이그레이션 | `backend/src/main/resources/db/migration/V50__public_finance_fallback.sql` |

## 5. GitHub Actions로 전체 재실행하기

1. GitHub 저장소 → **Actions** → `Verify and deploy main to EC2` 선택
2. **Run workflow** 클릭
3. 다음 값으로 실행

```text
branch: main
dart_backfill: true
company_growth_publish: true
company_growth_report: false
```

4. 실행 로그에서 아래 순서가 모두 보여야 정상이다.

```text
DART backfill complete: ...
DART financial sync complete: storedAnnualStatements=...
Public finance fallback diagnostics: ...
Public finance fallback sync complete: storedAnnualStatements=...
COMPANY_GROWTH_DATASET_ROWS=...
COMPANIES=...
DART validated growth prediction sync complete: storedPredictions=...
COMPANY_GROWTH_MODEL_PUBLISHED=...
```

`company_growth_publish`는 검증을 통과한 모델만 운영 모델로 교체한다. 검증 실패 시 기존 운영 모델은 유지된다.

## 6. 공공데이터 보강이 0건일 때 점검 순서

1. `DATA_GO_KR_SERVICE_KEY`가 실제 운영 컨테이너에 주입됐는지 확인한다.
2. 진단 로그의 `confirmedCorporations`와 `registrationResolved`를 확인한다.
   - `registrationResolved=0`: DART 기업개황의 법인등록번호 확보 단계 문제
3. `missingDartYears`를 확인한다.
   - `0`: DART 행은 존재하므로 공공 보강 대상이 없음. 단, 숫자 컬럼이 비어 있는지 DB를 별도 점검한다.
4. `apiFailures>0`이면 `firstApiFailure`부터 확인한다.
   - `CONFIGURATION`: `DATA_GO_KR_SERVICE_KEY`가 비어 있음
   - `HTTP_REQUEST_FAILED`: 운영 서버에서 공공 API로의 네트워크 요청 실패
   - 공공 API 오류 코드: 키의 활용신청/승인 상태, 요청 파라미터를 확인
5. `missingDartYears>0`이고 `apiFailures=0`, `noPublicRecord>0`이면 해당 법인등록번호·연도에 공공 API가 보유한 재무 행이 없는 것이다.
6. `apiRecords>0`인데 `storedAnnualStatements=0`이면 DB upsert/마이그레이션을 점검한다.

### 운영 서버에서 빠른 확인

```bash
cd /opt/jobpilot/jobpilot-ai
sudo docker compose --env-file /etc/jobpilot/jobpilot.env -f docker-compose.prod.yml logs --since=70m backend \
  | grep -E 'Public finance fallback diagnostics|Public finance fallback sync complete'
```

## 7. 왜 첫 수집이 오래 걸리는가

초기 실행은 회사별·연도별로 결측 재무를 확인한다. 공공 API는 법인등록번호(`crno`)와 사업연도(`bizYear`) 단위 조회이므로 첫 적재에 시간이 걸릴 수 있다.

하지만 이미 DART 데이터가 있는 연도는 공공 API를 호출하지 않고, DB에 저장한 데이터는 다음 실행에서 재사용한다. 즉 첫 정비 이후에는 새 회사·새 연도 중심의 증분 작업이 된다.

수동 재무 복구 배치에서는 Docker가 이전 JAR을 재사용하지 않도록 백엔드를 `--no-cache`로 빌드한다.

## 8. 배포 후 서비스 확인

```text
https://job-a-dream.site/api/v1/job-postings?financialsOnly=true&size=60&page=0
```

개별 공고의 재무 분석 API:

```text
https://job-a-dream.site/api/v1/job-postings/{jobPostingId}/company-finance
```

확인할 값:

- `financialsOnly` 결과의 총 공고 수
- 회사별 재무 연도 수
- `growthPrediction` 존재 여부
- 화면의 문구가 ‘성장 가능성’·‘예측 지표’로 표시되는지

## 9. 작업 원칙

- 모든 변경은 `김한영브뤤취학원`에서 커밋·푸시한다.
- PR로 `main`에 병합한 후에만 운영 배포한다.
- 재무 API 키·개인정보·운영 DB 접속정보는 커밋하지 않는다.
- 예측 결과가 적으면 숫자를 꾸며내지 않고, 매칭 수·연도 수·검증 수치를 먼저 공개한다.
