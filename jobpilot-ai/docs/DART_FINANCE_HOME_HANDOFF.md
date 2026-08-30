# JobPilot DART 기업 재무분석 - 집 작업 인수인계

작성일: 2026-08-29 (KST)  
목적: 채용공고 상세에서 해당 기업의 DART 재무 현황과, 검증된 ML 기반 성장 전망을 보여준다.

> 중요: API 키, GitHub 토큰, DB 비밀번호는 이 문서·커밋·로그에 절대 넣지 않는다. 모두 서버의 `/etc/jobpilot/jobpilot.env`에서만 관리한다.

## 1. 최종 사용자 경험 목표

새 메뉴나 별도 페이지를 만들지 않는다.

1. 사용자가 **전체 채용공고 → 공고 상세**로 들어간다.
2. 상세 상단의 `기업 재무 분석` 버튼을 누르면 같은 페이지의 `#company-finance` 영역으로 스크롤한다.
3. 영역 맨 위에는 ML 예측 설명을 보여준다.
   - 예: `성장 전망: 긍정적`
   - 근거: 최근 3년 매출 추이, 영업이익, 부채비율, 영업현금흐름, 개발직 채용 변화
   - 단, 모델 평가를 통과해 저장된 예측이 없으면 점수·전망을 만들지 않는다.
4. 그 아래에 기존 JobPilot 카드/버튼/타이포그래피와 같은 디자인으로 매출·영업이익·순이익 그래프, 재무 안정성, 채용 확장성을 표시한다.
5. DART 매칭/재무제표/연도 데이터가 부족한 경우에는 사실에 맞는 안내만 보여준다.

필수 상태:

| 상태 | 화면 메시지 원칙 |
| --- | --- |
| `READY` | 검증·저장된 예측과 재무 그래프 표시 |
| `FINANCIALS_ONLY` | 재무 그래프만 표시, 예측 점수는 표시하지 않음 |
| `UNMATCHED` | `DART 공시법인과 연결할 수 없어 재무정보를 제공하지 않습니다.` |
| `FINANCIALS_NOT_FOUND` | `이 기업은 DART에서 조회 가능한 재무제표를 찾지 못했습니다.` |
| `DATA_INSUFFICIENT` | 최근 3개년 미달이라 예측 불가라고 표시 |
| `TEMPORARILY_UNAVAILABLE` | 일시 오류/재시도 안내 |

## 2. 데이터 현실성 및 매칭 원칙

- DART는 모든 국내 기업 DB가 아니다. 상장사·외부감사 대상 등 공시법인 중심이다.
- 원티드에는 비상장 스타트업이 많으므로 전체 공고가 매칭될 것이라고 가정하면 안 된다.
- 회사명 정규화 뒤 **정확 일치**일 때만 `CONFIRMED`다.
- 부분 포함 등 애매한 경우는 `CANDIDATE`이고, 재무제표/예측에 절대 사용하지 않는다.
- DART에 법인은 있으나 재무제표가 없을 수 있으므로 매칭률과 재무 3개년 확보율은 별도로 보고한다.

## 3. 현재 구현 상태

### 이미 main에 병합/배포된 기반

- DART 법인목록 ZIP 다운로드 및 `dart_corporations` 적재
- 원티드 등 `job_postings`의 고유 회사 식별자별 회사명 정규화
- `CONFIRMED / CANDIDATE / UNMATCHED` 매칭 및 `company_dart_matches` 저장
- 기존 공고 백필 실행기: `DART_BACKFILL_ON_START=true`일 때만 실행
- DART 연간 재무제표 JSON 파서(CFS 연결재무제표 기준)
- Flyway용 재무 테이블 스키마

### 반드시 확인할 복구 커밋

`V41__dart_company_finance.sql`이 기존 V41과 충돌해 백엔드가 재시작 루프에 빠졌었다. 수정본은 다음이다.

- 브랜치: `codex/dart-flyway-migration-fix`
- 커밋: `fe7bee5 fix: resolve DART Flyway migration version`
- 수정: DART 마이그레이션을 `V47__dart_company_finance.sql`로 변경

배포가 성공했다면 이 수정은 이미 반영됐을 가능성이 높다. 다음으로 확인한다.

```bash
cd /opt/jobpilot/jobpilot-ai
git log --oneline -5
ls backend/src/main/resources/db/migration/V47__dart_company_finance.sql
```

### 구현되어 있고 PR/병합이 필요한 상세 API 기반

- 브랜치: `codex/dart-company-finance-detail`
- 커밋: `77445b9`, `aa2aa3c`
- 비교/PR: `https://github.com/hanyeong0605-prog/Final_Project/compare/main...codex%2Fdart-company-finance-detail?expand=1`

포함 내용:

- `CompanyFinancialSyncService`
  - `CONFIRMED` 법인만 조회
  - 최근 완료 연도별 CFS 재무제표를 `company_financial_years`에 UPSERT
  - DART 요청 실패/재무제표 미존재는 저장하지 않고 다음 법인으로 진행
- `DART_FINANCIAL_SYNC_ON_START=true` 옵션
  - `DART_BACKFILL_ON_START=true`와 함께 쓸 때, 매칭 후 최근 3개 완료 연도를 수집
  - 대량 호출이므로 처음에는 매칭 결과를 확인한 뒤 의도적으로 한 번만 켠다.
- 읽기 API
  - `GET /api/v1/job-postings/{id}/company-finance`
  - 실시간 DART 호출을 하지 않고 DB에 저장된 사실만 반환
  - `UNMATCHED`, `FINANCIALS_NOT_FOUND`, `DATA_INSUFFICIENT`, `FINANCIALS_ONLY`를 구분

검증: 관련 단위/컨트롤러 테스트와 당시 전체 백엔드 테스트를 실행했다. 기존 테스트의 Mockito 동적 에이전트 경고와 다른 모듈 로그 경고는 있었지만 Maven 종료코드는 0이었다.

## 4. 실제 매칭 결과 확인 (가장 먼저 할 일)

2026-08-29 10:37:56 KST 서버 로그로 측정한 결과는 다음과 같다.

| 항목 | 기업 수 | 비율 |
| --- | ---: | ---: |
| DART 법인목록 | 118,804 | - |
| 프로젝트 고유 회사 | 1,727 | 100.0% |
| 확정 매칭 (`CONFIRMED`) | 494 | 28.6% |
| 후보 (`CANDIDATE`) | 929 | 53.8% |
| 미매칭 (`UNMATCHED`) | 304 | 17.6% |

재무제표·예측에 사용할 수 있는 대상은 **확정 매칭 494개**뿐이다. 후보 929개는 이름이 부분적으로만 유사하므로 자동 승격하지 않는다. 후보를 무리하게 포함하면 다른 법인의 재무제표를 보여주는 치명적인 오매칭이 생긴다.

서버에서 아래 명령을 실행한다.

```bash
cd /opt/jobpilot/jobpilot-ai
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml logs --since 60m backend | grep "DART backfill complete"
```

정상 로그 형식:

```text
DART backfill complete: corporations=..., distinctCompanies=..., confirmed=..., candidates=..., unmatched=...
```

보고 계산식:

- 확정 매칭률 = `confirmed / distinctCompanies * 100`
- 후보 비율 = `candidates / distinctCompanies * 100`
- 미매칭 비율 = `unmatched / distinctCompanies * 100`
- 재무 3개년 확보율은 금융 동기화 뒤 `3개년 이상 financial_years 보유 CONFIRMED 법인 / confirmed`로 별도 계산

로그가 없다면 `DART_BACKFILL_ON_START=true`가 backend 컨테이너에 전달되지 않은 것이다. 키를 출력하지 않고 존재 여부만 확인한다.

```bash
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml exec backend sh -c \
  'test -n "$DART_API_KEY" && echo DART_API_KEY=SET || echo DART_API_KEY=MISSING'
```

## 5. 배포 절차

환경파일(`/etc/jobpilot/jobpilot.env`)에는 다음 키가 이미 필요하다.

```ini
DART_API_KEY=실제_비밀값
DART_BACKFILL_ON_START=true
# 재무제표 최초 수집을 실행할 때만 한 번 true
DART_FINANCIAL_SYNC_ON_START=false
VITE_KAKAO_MAP_KEY=...
VITE_TOSS_CLIENT_KEY=...
```

`DART_BACKFILL_ON_START`의 의미:

- 기존 2,000개 이상 공고의 고유 회사를 한 번 매칭하기 위한 **초기 백필 테스트/초기화 스위치**다.
- 매일 켜둘 기능이 아니다. 매칭 결과를 받은 뒤 `false`로 바꾼다.

배포 명령:

```bash
cd /opt/jobpilot/jobpilot-ai
git pull
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml \
  up -d --build backend frontend
```

Compose에 `--env-file`을 넣는 이유는 frontend 빌드가 `VITE_KAKAO_MAP_KEY`, `VITE_TOSS_CLIENT_KEY`를 Compose 변수 치환 시점에 요구하기 때문이다.

배포 후 확인:

```bash
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml ps
```

## 6. 권장 실행 순서

1. `V47` 복구 커밋이 main에 있는지 확인하고 backend가 healthy인지 확인한다.
2. `DART_BACKFILL_ON_START=true`, `DART_FINANCIAL_SYNC_ON_START=false`로 한 번 배포한다.
3. `DART backfill complete` 로그를 확보해 매칭 결과를 보고한다.
4. 백필 스위치를 `false`로 되돌린다.
5. `codex/dart-company-finance-detail` PR을 main에 병합·배포한다.
6. 매칭률과 API 사용량을 확인한 뒤에만 `DART_BACKFILL_ON_START=true`, `DART_FINANCIAL_SYNC_ON_START=true`로 한 번 실행한다.
7. `DART financial sync complete: storedAnnualStatements=...` 로그와 3개년 확보율을 기록한다.
8. 두 스위치를 모두 `false`로 되돌린다.
9. UI와 ML 단계를 진행한다.

## 7. 남은 구현 작업

### A. 재무 데이터 품질 강화

- CFS가 없을 때 OFS(별도재무제표) fallback 구현
- DART 응답의 `rcept_no`, 통화, 보고서 출처까지 정확히 저장
- `FinancialMetricCalculator`: 매출 성장률, 영업이익률, 부채비율, 영업현금흐름 비율 계산
- 06:00 크롤링 완료 이벤트 뒤 새롭거나 오래된 **고유 회사만** 큐/배치에 넣기
- 공고마다 DART를 호출하지 않는다.

### B. ML 학습과 예측

학습 데이터는 프로젝트 기업이 아니라 DART에서 확보한 여러 공시법인의 회사-연도 행으로 만든다.

- 입력 연도 `t`에 이미 공개된 최근 3개년 재무지표와 채용지표만 feature로 사용
- 라벨은 `t+1` 실제 결과
  - 매출 성장 여부/성장률
  - 수익성 개선
  - 재무 안정성 위험
- 시간 순서 분할: 과거 연도 학습, 더 최근 연도 검증/홀드아웃
- 같은 회사의 미래 재무제표가 과거 feature에 섞이지 않게 한다(데이터 누수 방지).
- 평가를 통과한 모델만 `model_version`, 평가 지표, 확률, 근거와 함께 저장한다.
- 평가 없는 모델·LLM 추측은 `READY` 예측으로 노출하지 않는다.

### C. 프론트엔드 상세 UI

수정 예정 파일(원칙):

- `frontend/src/pages/JobPostingDetailPage.tsx`
- `frontend/src/features/company-finance/...` 신규 기능 폴더
- `frontend/src/styles.css`에는 `.company-finance-*` 범위로 최소 추가

조건:

- 새 메뉴/라우트 생성 금지
- 기존 프로젝트 카드·outline 버튼·색·글꼴·반응형을 그대로 사용
- HTML스럽거나 오래된 표/버튼 형태 금지
- 그래프 데이터가 없으면 0 막대 그래프를 만들지 않는다.

## 8. 주요 파일 위치

```text
jobpilot-ai/backend/src/main/resources/db/migration/V47__dart_company_finance.sql
jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/companyfinance/
jobpilot-ai/backend/src/test/java/com/jobpilot/api/domain/companyfinance/
jobpilot-ai/docs/superpowers/specs/2026-08-29-dart-company-finance-design.md
jobpilot-ai/docs/superpowers/plans/2026-08-29-dart-company-finance.md
jobpilot-ai/docs/DART_FINANCE_HOME_HANDOFF.md
```

## 9. Git 작업 주의사항

- 사용자가 요구한 기준 브랜치는 `김한영브뤤취학원`이었다.
- 이미 main에 반영된 DART 기반 PR은 `#169`으로 보인다.
- 이후 수정은 최신 main에서 새 작업 브랜치를 만들고, 대상 브랜치를 확인한 뒤 PR을 만든다.
- Flyway 번호는 반드시 최신 main의 최대 번호보다 큰지 먼저 확인한다.
- 기존 워크트리에는 다른 팀원의 수정 파일이 많으므로 `git reset --hard`, 광범위 삭제, 무관 파일 포맷팅을 하지 않는다.

## 10. 이 대화에서 확정된 의사결정 요약

- DART는 재무제표의 매출/영업이익/순이익/자산/부채/자본/현금흐름 같은 공시 사실을 제공한다.
- 재무제표는 기업의 성장·하락 추세를 보는 근거 중 하나지만, 미래를 보장하지 않는다.
- 예측은 DART의 다른 기업 과거 데이터를 학습해, 매칭된 프로젝트 기업에 적용하는 방식이 맞다.
- 프로젝트의 핵심 리스크는 원티드 회사와 DART 공시법인의 매칭 커버리지다.
- 재무/예측은 공고 상세 화면 안에 두고, 최상단에 설명형 예측 카드를 배치한다.
- 데이터 없음은 제품 품질 저하가 아니라 정상 상태로 정직하게 보여준다.
