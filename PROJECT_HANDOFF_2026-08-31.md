# JobADream 작업 인수인계서 — 2026-08-31

## 현재 상황

- 작업 브랜치: `김한영브뤤취학원`
- 마지막 푸시 커밋: `af636e48 fix: calibrate community feedback sentiment`
- 이 문서는 민감한 API 키, 서버 IP, 토큰을 포함하지 않는다.
- 사용자는 `main`에 머지된 뒤 GitHub Actions의 **Verify and deploy main to EC2**로 EC2를 배포한다.
- 현재 화면에 보인 수동 배포 실행은 실패가 아니라 취소 상태였다. `production-deploy` 동시 실행 제한 때문에 더 최신 요청이 들어오면 기존 실행이 취소된다.
- 최신 Actions 화면 기준: PR #204의 브랜치 CI, `main` 머지 CI, `Verify and deploy main to EC2`가 각각 `In progress` 또는 `Queued`였다. 이는 정상적인 대기열 상태다. 같은 배포를 다시 실행하지 말고, 최신 `main` 배포 1건이 완료될 때까지 기다린다. 장시간(약 15분 이상) `Queued`에서 전혀 변하지 않을 때만 해당 실행 상세의 concurrency/runner 오류를 확인한다.

## 최우선 재개 순서

1. `김한영브뤤취학원` 브랜치의 최신 변경을 PR로 `main`에 머지한다.
2. Actions 목록에서 가장 최신 `main` 머지의 **Verify and deploy main to EC2**가 초록색으로 완료되는지 확인한다.
3. 게시판 `/community`에서 목록 요청이 200으로 돌아오는지 확인한다.
4. 그 다음에만 수동 workflow를 실행한다. 이전 실행이 취소되었다면 재실행하지 말고 최신 main 배포가 끝날 때까지 기다린다.

## 회사 재무·성장 예측

### 현재 구조

- 예측 화면 대상: 서비스의 채용공고 기업 중 DART 법인 매칭 및 최근 3개년 재무가 있는 기업.
- 학습 표본: 채용공고 기업에 한정하면 안 된다.
- 학습 행: 기준연도 `t`의 직전 2년과 현재년(`t-2`, `t-1`, `t`)을 특성으로 사용하고, 다음 해(`t+1`) 실제 결과를 레이블로 사용한다.
- 따라서 **학습 데이터는 연속 4개년**이 필요하지만, 실제 채용공고 기업의 **추론은 3개년** 재무만 있으면 된다.
- `ml/company_finance_dataset.py`는 이미 `company_financial_years` 전체를 읽어 학습 CSV를 생성한다. 기존 병목은 재무 동기화가 채용공고 매칭 기업만 저장했다는 점이었다.

### 새 학습용 DART 수집 배치

커밋 `bd668730 feat: build independent DART training universe`에 추가됨.

- DART 상장사 중 `corp_code` 순으로 최대 800개를 선정한다.
- 최근 7개 사업연도의 연결 재무를 100개 기업 단위 다건 OpenDART API로 수집한다.
- 이미 저장된 회사·연도는 건너뛰므로, DART 요청 한도로 중단되어도 다음 실행에서 누락 연도만 재개한다.
- 채용공고 기업 매칭은 학습 범위를 제한하지 않고, 예측 결과를 표시할 기업 선택에만 사용한다.
- `CompanyFinancialSyncService.syncTrainingUniverse(...)`가 구현되어 있다.
- 실행 입력: GitHub Actions의 `Build an 800-company DART learning universe for the growth model (not limited to job postings)`.

### 실행 방법

`main` 최신 배포가 끝난 뒤 Actions → **Verify and deploy main to EC2** → `Run workflow`에서:

1. Branch: `main`
2. 우선 `Build an 800-company DART learning universe...`만 체크한다.
3. 완료 로그에서 다음을 확인한다.

```text
DART training universe sync complete: corporations=800, storedAnnualStatements=...
```

4. 다음 수동 실행에서 `Build a temporary company-growth dataset and print held-out metrics`만 체크한다.
5. 로그의 아래 값을 확인한다.

```text
COMPANY_GROWTH_DATASET_ROWS=...
COMPANIES=...
LABEL_RATES
```

6. 연속 4개년 데이터가 있는 기업 수가 최소 500개 이상인지, held-out 검증 지표가 납득 가능한지 확인한 뒤에만 `Train, validate, publish the company-growth model...`을 실행한다.

### 공공데이터 재무 보강

- 구현 목적: DART 법인 매칭에 이미 성공한 채용공고 기업의 누락 재무 연도를 보강한다.
- 처리: DART 법인코드 → 법인등록번호 → 공공 재무 API.
- 공공데이터가 새로운 채용공고 기업을 이름으로 찾아 매칭 범위를 넓히는 기능은 아니다.
- 이전 실행에서는 OpenDART 요청 제한이 발생했고, 공공 fallback이 실제 저장에 성공한 수치를 아직 확인하지 못했다.
- 관련 개선 커밋: `91a26bf0 fix: continue public finance after DART quota pause`.
- 이 검증은 800개 학습 배치와 섞지 말고, 학습 수집·검증이 끝난 후 별도 수동 실행으로 한다.
- 별도 실행 시 기존 `Run one-shot OpenDART company matching and seven-year financial backfill`만 체크한다.
- 확인 로그:

```text
Public finance fallback diagnostics: ... apiRecords=... storedAnnualStatements=...
```

- `apiRecords > 0`: 공공 API 레코드 수신.
- `storedAnnualStatements > 0`: 실제 DB 누락 연도 보강 성공.

### 기존 재무 관련 변경

- `1e136a18`: 재개 가능한 DART/공공 재무 복구 및 플래너 날짜 안정화.
- `3b040b28`: DART 당기순이익(손실) 계정 라벨 처리 및 누락 순이익 재수집.
- `3edbde75`: 값 없는 재무 차트·카드를 숨김.
- `c5402177`: 성장 예측 카드에서 제공받은 MP4를 약 1.15초 재생한 뒤 예측값을 표시.
- 재무 화면의 `company-growth-rf-v1-cutoff-2024` 내부 모델 버전 문자열은 사용자 화면에서 제거했다.
- 녹십자홀딩스 예시의 2025 매출 2.5조/영업이익 362억은 오류가 아니라 연결재무 기준으로 공시·공식 IR과 일치한다.

## 커뮤니티·감정분석

### 게시판 재구성

커밋 `712828bc feat: rebuild community board and mood dashboard`에 추가됨.

- `/community`: 자유게시판 목록을 기본으로 표시.
- 자유게시판/ Q&A 탭, 제목·내용 검색, 최신/조회/좋아요 정렬, 서버 기준 10개 단위 페이지네이션.
- 글쓰기 화면: `/community/write`.
- 상세 화면: `/community/:id`.
- 기존의 목록 옆 작성 폼을 제거했다.
- `prompt()` 팝업 수정 방식을 제거하고, 상세 화면 내부 편집 폼으로 변경했다.
- 비공개 Q&A는 권한 있는 사용자만 열람하며, 감정 분석에는 포함하지 않는다.

### 게시판 502 수정

커밋 `d897a354 fix: restore paginated community list query`에 추가됨.

- 증상: `GET /api/v1/community/posts?...`가 502.
- 원인: 페이지 목록 SQL 조합 중 `FROM community_posts`가 중복되어 SQL 예외 발생.
- 수정: 목록 SQL의 `WHERE` 절만 연결하도록 변경.
- 이 커밋까지 main에 머지·배포되어야 게시판 502가 해결된다.

### 감정분석 관리자 화면

- 공개 자유게시판과 공개 Q&A를 모두 분석 대기열에 넣는다.
- 이전에는 작성자가 `서비스 피드백`을 체크한 글만 분석했다.
- 관리자 페이지는 게시판별로 아래를 표시한다.
  - 공개 글 수 / 분석 대기 수
  - 긍정 글 수 / 부정 글 수
  - 긍정 표현 수 / 부정 표현 수
  - 최근 글 5개 제목 및 감정 판정 미리보기
- 감정 판정은 기존 AI 감정 모델 결과를 사용한다.
- 표현 수는 운영자가 빠르게 분위기를 확인할 수 있도록 한국어 긍정/부정 신호어를 세는 보조 지표다. 모델 자체의 단어 추출 결과로 오해하면 안 된다.
- DB 마이그레이션: `V53__queue_public_community_sentiment.sql`이 기존 공개 글 중 `SKIPPED` 상태를 `PENDING`으로 전환한다.
- 실제 분석 수치는 백엔드 감정 워커와 AI 서버가 정상 설정·실행되어야 채워진다. 비공개 Q&A는 제외한다.

### 부정 표현·부정 글 보정 (최신)

커밋 `c4d189a5`, `af636e48`에 추가됨.

- 문제 사례: `채용공고가 너무 IT 편향적이라 부족해요`가 기존 화면에서 긍정/부정 표현 모두 0개, 부정 글 0개로 표시됐다.
- 원인: 관리자 화면의 보조 신호어 목록에 `편향`, `부족` 등이 없었고, 범용 감정 모델이 서비스 맥락의 불만을 `NEUTRAL`로 분류할 수 있었다.
- 표현 집계에 `편향`, `부족`, `한정`, `제한`, `아쉽`, `불균형`, `차별`, `불공정`, `미흡`, `불안정`, `복잡`, `힘들` 등을 추가했다.
- AI의 원 판정이 `NEUTRAL`이면서 위와 같은 명확한 부정 신호가 있으면, **커뮤니티 관리자 통계에서만** `NEGATIVE`로 보정한다. 이는 범용 모델을 재학습했다고 주장하는 기능이 아니라 서비스 피드백을 놓치지 않기 위한 명시적 운영 규칙이다.
- `V54__requeue_community_feedback_calibration.sql`이 배포 시 기존 공개 글을 다시 `PENDING`으로 전환한다. 감정 워커가 활성화돼 있고 AI 서버 연결이 정상이라면 재분석 뒤 새 통계가 나온다.
- 해당 문장은 재분석 후 부정 표현 최소 2개(`편향`, `부족`) 및 부정 글로 반영되는 것이 기대 결과다.

## 회사 리뷰 폐지

커밋 `9c0a8f75 refactor: retire company review feature`.

- 채용공고 상세의 회사 리뷰 UI, 조회/저장 API, 엔티티/리포지토리를 제거했다.
- `V52__remove_job_posting_reviews.sql`이 `job_posting_reviews` 테이블과 기존 리뷰 데이터를 삭제한다.
- 이 기능은 의도적으로 복구 대상이 아니다.

## 플래너

- 자격증 시험 회차를 같은 자격증·시험 유형이라도 시작일 기준으로 여러 개 저장할 수 있도록 유니크 키를 변경했다 (`e8bd681e`).
- 플래너에서 자동 생성된 채용공고/자격증 일정의 X 삭제는 찜 목록과 연결되어, 원본 찜도 함께 해제하도록 구현되어 있다.

## 검증 현황

- 커뮤니티 개편 시 백엔드 전체 Maven 테스트가 통과했다. (111 tests, 4 skipped)
- 독립 DART 학습 범위 추가 후 `CompanyFinancialSyncServiceTest`, `DartCompanyFinanceBackfillRunnerTest`가 통과했다.
- 로컬 프런트 전체 `npm run build`는 기존 환경의 Vitest 타입/Windows node_modules 문제 때문에 신뢰 가능한 전체 검증 수단이 아니었다. GitHub CI에서 `npm ci && npm run build`가 수행된다.

## 작업 트리 주의사항

다른 사람이 만든 것으로 보이는 아래 항목은 인수인계 작업에서 건드리지 않는다.

- `DartCompanyFinanceBackfillRunner.java`의 별도 주석 수정(작업자의 변경과 겹치지 않도록 주의).
- 루트의 `JobADream_*.html`, `hero-chatbot-cleanup/`, `hero-mascot-visual/`, `ppt-assets/`, `pr73-resolve/` 등 untracked 항목.
- `jobpilot-ai/ai-server/.env.root-copy.bak`는 절대 커밋하거나 내용을 노출하지 않는다.

## 핵심 원칙

- 학습 데이터와 서비스 예측 대상은 분리한다.
- 모델을 학습·검증하지 않은 채 운영 모델로 승격하지 않는다.
- DART 쿼터 중단은 실패로 단정하지 말고, 저장된 연도를 건너뛰는 재개 배치를 사용한다.
- 공공데이터 fallback의 성공 여부는 반드시 로그 수치로 확인한다.
- API 키·토큰·서버 환경값은 Git, 스크린샷, 인수인계서에 넣지 않는다.
