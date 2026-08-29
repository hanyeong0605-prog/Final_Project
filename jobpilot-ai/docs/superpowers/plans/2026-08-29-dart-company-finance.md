# DART Company Finance Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show source-traceable DART financial analysis and a validated ML growth outlook inside each matched job-posting detail page.

**Architecture:** Spring Boot owns DART ingestion, legal-company matching, financial persistence, and the public detail API. The Python AI service builds a versioned company-year training set and model artifact from persisted financial history; Spring stores only validated model output. React renders a same-page anchor, the outlook card, and responsive charts using the existing design system.

**Tech Stack:** Spring Boot 3.4/JPA/Flyway/MySQL, Python FastAPI/pandas/scikit-learn/joblib, React/TypeScript, existing Lucide icons and CSS.

**Spec:** `jobpilot-ai/docs/superpowers/specs/2026-08-29-dart-company-finance-design.md`

## Global Constraints

- Keep `DART_API_KEY` server-only; never return or log it.
- Use only `CONFIRMED` company matches for financial data and predictions.
- Store DART report provenance and never fabricate missing values or prediction scores.
- Use time-based splits; never train with a company-year's future disclosure.
- Place analysis inside `/job-postings/:id`; add no route or navigation menu.
- Reuse existing cards, typography, colors, buttons, icon sizing, and responsive breakpoints.

---

### Task 1: Create persistent DART and prediction schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V41__dart_company_finance.sql`
- Create: `backend/src/test/resources/db/migration/V41__dart_company_finance_test.sql`

**Interfaces:**
- Produces tables `dart_corporations`, `company_dart_matches`, `company_financial_years`, `company_financial_metrics`, `company_hiring_monthly_metrics`, and `company_growth_predictions`.
- `company_dart_matches` identifies a source company by `(source_provider, source_company_id, normalized_company_name)` and references `dart_corporations.corp_code`.

- [ ] Write the Flyway migration test asserting every table, unique key, FK, and provenance column exists.
- [ ] Run the migration test and confirm it fails because V41 does not exist.
- [ ] Add V41 with `corp_code CHAR(8)`, annual fiscal keys, report receipt number, `fs_div`, raw monetary values, calculated metrics, match state enum-as-VARCHAR, model version, input snapshot JSON, and created/updated timestamps.
- [ ] Run the migration test and `mvn test` to confirm the schema validates.
- [ ] Commit only the migration and test.

### Task 2: Implement deterministic company matching

**Files:**
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/entity/DartCorporation.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/entity/CompanyDartMatch.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/repository/DartCorporationRepository.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/repository/CompanyDartMatchRepository.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyNameNormalizer.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyDartMatchingService.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/service/CompanyNameNormalizerTest.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/service/CompanyDartMatchingServiceTest.java`

**Interfaces:**
- `String CompanyNameNormalizer.normalize(String rawName)` returns a non-empty normalized name or `""`.
- `CompanyDartMatchingService.match(JobPosting posting)` returns only `CONFIRMED`, `CANDIDATE`, or `UNMATCHED`; only exact normalized names are `CONFIRMED`.

- [ ] Write failing normalizer tests for `주식회사 플리토`, `(주)플리토`, whitespace, punctuation, and null.
- [ ] Run the test and confirm the normalizer class is missing.
- [ ] Implement the minimal normalizer that removes corporation markers, whitespace, punctuation and lowercases text.
- [ ] Run the normalizer tests and confirm they pass.
- [ ] Write a failing matching test proving an exact normalized match becomes `CONFIRMED` and a similarity-only candidate cannot become confirmed.
- [ ] Implement repository-backed exact matching and persistence of match method/confidence/status.
- [ ] Run matching tests and commit this task.

### Task 3: Add a resilient OpenDART client and annual financial collector

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/client/OpenDartClient.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/client/OpenDartHttpClient.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/DartCorporationSyncService.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyFinancialSyncService.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/FinancialMetricCalculator.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/client/OpenDartHttpClientTest.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/service/FinancialMetricCalculatorTest.java`

**Interfaces:**
- `OpenDartClient.downloadCorporations()` returns DART corporate-code rows.
- `OpenDartClient.fetchAnnualStatements(String corpCode, int businessYear, String fsDiv)` returns typed account rows with receipt/report provenance.
- `FinancialMetricCalculator.calculate(CompanyFinancialYear year, CompanyFinancialYear priorYear)` returns nullable, denominator-safe metrics.

- [ ] Write a failing HTTP client test using a local mock response: verify the key is sent in a request but excluded from exception text and logs.
- [ ] Write failing calculator tests for growth, margin, debt ratio, missing prior-year values, and zero equity.
- [ ] Run tests and confirm missing classes fail.
- [ ] Add `dart.api-key: ${DART_API_KEY:}` and timeouts; fail closed with a configuration error if the key is absent.
- [ ] Implement ZIP/XML corporation parsing and JSON annual-statement parsing; select consolidated statements first and separate statements only if consolidated data is absent.
- [ ] Persist annual raw values and calculated metrics with receipt number, report code, and `fs_div`.
- [ ] Run focused tests, then all backend tests, and commit this task.

### Task 4: Expose cached company finance analysis for a job posting

**Files:**
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/dto/CompanyFinanceAnalysisResponse.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyFinanceAnalysisService.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/controller/CompanyFinanceController.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/service/CompanyFinanceAnalysisServiceTest.java`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/controller/CompanyFinanceControllerTest.java`

**Interfaces:**
- `GET /api/v1/job-postings/{id}/company-finance` returns status `READY`, `FINANCIALS_ONLY`, `UNMATCHED`, or `DATA_INSUFFICIENT`.
- Response contains annual chart series, forecast only when persisted, hiring metrics, source metadata, and no secrets.

- [ ] Write a failing service test for a confirmed match with three annual statements returning chart series and `FINANCIALS_ONLY`.
- [ ] Write failing tests for unmatched and insufficient-history status, ensuring neither returns a forecast.
- [ ] Implement a read-only service that maps stored facts to the response without live DART calls.
- [ ] Add controller tests for 200 responses and 404 job posting behavior.
- [ ] Run focused tests and all backend tests; commit this task.

### Task 5: Build reproducible ML dataset, training, and evaluation artifacts

**Files:**
- Create: `ai-server/ml/company_finance_dataset.py`
- Create: `ai-server/ml/train_company_growth_model.py`
- Create: `ai-server/ml/company_growth_model.py`
- Create: `ai-server/tests/test_company_finance_dataset.py`
- Create: `ai-server/tests/test_train_company_growth_model.py`
- Create: `docs/company-finance-ml.md`

**Interfaces:**
- `build_company_year_dataset(financial_rows)` emits one row per `(corp_code, base_year)` with only values known by `base_year` and next-year labels.
- `train_and_evaluate(dataset, cutoff_year)` returns a versioned artifact, feature manifest, MAE, classification metrics, calibration output, and holdout period.
- Model input requires three consecutive annual periods; output includes growth probability, profitability-improvement probability, stability-risk probability, and confidence state.

- [ ] Write failing dataset tests showing 2021 features pair only with 2022 labels and rows lacking three prior years are excluded.
- [ ] Run pytest and confirm the module is absent.
- [ ] Implement feature construction: 1/3-year revenue growth, operating margin, debt ratio, operating cashflow ratio, profitability sign, industry and size bucket.
- [ ] Write failing trainer tests proving the holdout year is excluded from training and an artifact stores feature names and metrics.
- [ ] Implement deterministic time-split training, probability calibration, model serialization, and metric report generation.
- [ ] Document source schema, label formulas, anti-leakage rules, train command, evaluation metrics, and retraining procedure in Korean.
- [ ] Run the focused pytest suite and commit this task.

### Task 6: Persist validated model predictions

**Files:**
- Create: `ai-server/app/domain/companyfinance/router.py`
- Modify: `ai-server/app/main.py`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/client/CompanyGrowthModelClient.java`
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/service/CompanyGrowthPredictionService.java`
- Test: `ai-server/tests/test_router_companyfinance.py`
- Test: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/service/CompanyGrowthPredictionServiceTest.java`

**Interfaces:**
- Internal AI endpoint accepts one three-year feature snapshot and returns a model version, probabilities, confidence, and feature contributions.
- Spring persists prediction only if its model metadata contains successful held-out evaluation; otherwise it retains `FINANCIALS_ONLY`.

- [ ] Write a failing FastAPI test for rejecting incomplete three-year feature input.
- [ ] Implement the internal prediction endpoint with no raw DART key access.
- [ ] Write a failing Spring service test proving an unvalidated model response is not persisted or exposed.
- [ ] Implement internal-client authentication, result validation, persistence, and a scheduled refresh hook.
- [ ] Run Python and backend focused tests, then commit this task.

### Task 7: Add the in-page analysis UI using existing design language

**Files:**
- Create: `frontend/src/features/company-finance/api/companyFinanceApi.ts`
- Create: `frontend/src/features/company-finance/model/companyFinance.types.ts`
- Create: `frontend/src/features/company-finance/components/CompanyFinanceSection.tsx`
- Create: `frontend/src/features/company-finance/components/FinanceMetricChart.tsx`
- Modify: `frontend/src/pages/JobPostingDetailPage.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/features/company-finance/components/CompanyFinanceSection.test.tsx`

**Interfaces:**
- `getCompanyFinance(jobPostingId, init)` calls the company-finance endpoint.
- `CompanyFinanceSection` renders its status state, source-traceable outlook, charts, and hiring evidence.
- Hero `기업 재무 분석` button scrolls to `#company-finance` only after analysis data is available.

- [ ] Write a failing component test for `READY`: it must render the outlook heading, probability, exact evidence text, and data basis.
- [ ] Write failing tests for `UNMATCHED` and `FINANCIALS_ONLY`: no artificial forecast score may render.
- [ ] Run the test and confirm missing component failure.
- [ ] Implement typed API client and section using existing outline button/card styles, Lucide icons, semantic headings, and responsive chart labels.
- [ ] Add an anchor button to the existing job-detail hero and place the section after images, before the job description.
- [ ] Add CSS scoped under `.company-finance-*`; preserve the user's existing `styles.css` changes.
- [ ] Run frontend tests and production build; commit this task.

### Task 8: Run data-quality probe, integration verification, and publish the ML report

**Files:**
- Create: `backend/src/main/java/com/jobpilot/api/domain/companyfinance/admin/CompanyFinanceAdminController.java`
- Create: `backend/src/test/java/com/jobpilot/api/domain/companyfinance/CompanyFinanceIntegrationTest.java`
- Create: `docs/company-finance-validation-report.md`

**Interfaces:**
- Admin-only probe starts corporation sync/matching and returns counts for exact matches, confirmed matches, 3-year financial availability, and exclusions.
- Validation report records run date, DART scope, match precision sample, model holdout metrics, and known limits without secrets.

- [ ] Write a failing integration test for a confirmed sample posting with persisted figures and a source receipt link.
- [ ] Implement the admin probe and integration path with a mocked DART client for repeatable CI.
- [ ] Run the real server-side DART smoke test only after deployment confirms `DART_API_KEY=SET`; do not print the key.
- [ ] Write the Korean ML/validation report from actual generated counts and evaluation output; distinguish measured facts from future work.
- [ ] Run backend tests, AI tests, frontend build, and deployment health checks; commit this task.
