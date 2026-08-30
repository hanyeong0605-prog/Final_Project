# JobPilot DART 기업 재무분석 - 전체 대화 인수인계

작성일: 2026-08-29 KST  
대상 저장소: `hanyeong0605-prog/Final_Project` / `jobpilot-ai`  
목적: 집 환경에서도 이 문서 하나만 보고 DART 재무분석, ML 예측, 공고 상세 UI를 안전하게 이어서 구현·배포한다.

> 보안: DART API 키, GitHub PAT, DB 비밀번호, OAuth/Toss/Kakao 키의 **값을 이 문서·커밋·로그에 기록하지 않는다**. 이전 대화 중 노출된 GitHub PAT는 폐기/재발급 권장 대상이다.

---

## 1. 최종 제품 목표

사용자가 `전체 채용공고 → 공고 상세`로 들어갔을 때, 새 메뉴나 새 페이지 없이 같은 상세 페이지 안에서 해당 공고 회사의 재무 분석을 본다.

1. 상세 상단에 기존 디자인 시스템과 동일한 `기업 재무 분석` 버튼을 둔다.
2. 버튼은 `#company-finance` 섹션으로 부드럽게 스크롤한다.
3. 섹션 맨 위에 검증된 ML 전망 설명을 둔다.
   - 예: `성장 전망: 긍정적 ↑`
   - 근거: `매출 3년 연속 증가 · 개발직 채용 확대`
4. 아래에 매출/영업이익/당기순이익 추이, 부채비율·현금흐름, 개발 직군 채용 변화 그래프를 보여준다.
5. 예측은 모델 평가를 통과하고 DB에 저장된 결과가 있을 때만 표시한다. 절대 LLM 추측이나 임의 점수를 만들지 않는다.

참고 화면의 의도:

- 카드형 흰 배경, 여백이 충분한 현대적 대시보드 느낌
- 연도별 막대/선 그래프, 핵심 수치, 전년 대비 변화, 간단한 근거 문장
- 기존 JobPilot의 버튼·글꼴·색·아이콘·반응형을 그대로 따른다.
- HTML스럽거나 옛날 표/버튼 스타일 금지

## 2. 왜 DART인가, 그리고 한계

DART OpenAPI는 공시법인의 공개 재무제표 사실을 제공한다. 매출, 영업이익, 당기순이익, 자산, 부채, 자본, 영업현금흐름처럼 연도별 성장/하락을 판단할 근거가 된다. 그러나 미래 성장을 보장하는 데이터는 아니다.

핵심 한계:

- 원티드 IT 공고 기업에는 비상장 스타트업, 브랜드명, 해외 법인이 많다.
- DART는 모든 국내 기업 DB가 아니라 공시법인 중심이다.
- 따라서 "모든 공고에 재무제표"가 목표가 아니라, **정확히 연결된 회사에만 신뢰할 수 있는 분석**을 제공하는 것이 목표다.

## 3. 실제 매칭 결과 (측정 완료)

측정 로그: `2026-08-29T10:37:56.227Z`

```text
DART backfill complete: corporations=118804, distinctCompanies=1727,
confirmed=494, candidates=929, unmatched=304
```

| 구분 | 기업 수 | 비율 | 의미 |
| --- | ---: | ---: | --- |
| DART 법인목록 | 118,804 | - | 다운로드·DB 적재 완료 |
| 고유 프로젝트 기업 | 1,727 | 100% | 공고별이 아닌 회사 식별자별 |
| `CONFIRMED` | 494 | **28.6%** | 재무/예측에 사용할 수 있음 |
| `CANDIDATE` | 929 | 53.8% | 이름 유사, 자동 연결 금지 |
| `UNMATCHED` | 304 | 17.6% | DART 연결 불가 |

후보가 많은 이유는 `(주)`, `주식회사` 제거 뒤 정확 일치가 아니고, 부분 포함 관계만 있기 때문이다. 계열사·동명법인·브랜드명일 수 있으므로 후보 929개를 확정으로 올리면 타사 재무제표를 노출할 위험이 있다.

## 4. 매칭 정책과 커버리지 확대 방향

현재 정책:

- 정규화 후 정확 일치: `CONFIRMED`
- 부분 포함/유사: `CANDIDATE`
- 후보 없음: `UNMATCHED`
- 재무 수집과 사용자 노출은 `CONFIRMED`만 허용

커버리지 확대는 다음 순서로 한다.

1. 관리자 검수로 후보 승인/제외
2. 승인된 회사명 별칭을 저장해 향후 06시 크롤링 때 자동 재사용
3. 공식 홈페이지 도메인·사업자번호처럼 검증 가능한 보조 근거를 검수 화면에 표시
4. 필요 시 외부 기업정보 제공기관 계약으로 비상장 범위를 보완

절대 하지 않을 것: 문자열 유사도만으로 후보를 자동 확정하거나, 재무가 없을 때 0 그래프/가짜 전망을 보여주는 것.

후보 검수 설계: `docs/superpowers/specs/2026-08-29-dart-candidate-verification-design.md`

## 5. 화면 상태 문구

| 상태 | 사용자 처리 |
| --- | --- |
| `READY` | 검증된 모델 전망 + 재무/채용 그래프 |
| `FINANCIALS_ONLY` | 재무 그래프만, 예측 점수 없음 |
| `UNMATCHED` | `DART 공시법인과 연결할 수 없어 재무정보를 제공하지 않습니다.` |
| `FINANCIALS_NOT_FOUND` | `이 기업은 DART에서 조회 가능한 재무제표를 찾지 못했습니다.` |
| `DATA_INSUFFICIENT` | 최근 3개년 데이터 부족으로 성장 전망 불가 |
| `TEMPORARILY_UNAVAILABLE` | 일시 오류, 재시도 안내 |

재무정보가 없는 공고도 기존 공고·스킬·직무 매칭 기능은 정상 제공한다. 재무 섹션 부재가 제품 전체 실패처럼 보이면 안 된다.

## 6. ML 학습 설계

### 학습 데이터는 무엇인가

프로젝트 회사만으로 학습하지 않는다. DART에서 확보한 여러 공시법인의 **회사-연도(company-year)** 행을 만든다.

- 입력 시점 `t`: 그 시점에 공개된 최근 3개 연도 재무제표와 채용 지표
- 라벨 `t+1`: 다음 연도의 실제 매출 성장, 수익성 개선, 안정성 위험
- 프로젝트의 확정 매칭 회사에는 학습된 모델을 적용한다.

### Feature 후보

- 1년/3년 매출 성장률
- 영업이익률, 순이익 흑자 여부
- 부채비율 `(부채 / 자본)`
- 영업현금흐름 비율
- 자산/현금 규모 버킷
- 업종, 회사 규모 버킷
- 개발직 활성 공고 수와 증감, 주요 역할/스킬 요약

### Label 후보

- 다음 해 매출 증가/감소 또는 성장률
- 다음 해 영업이익 개선 여부
- 다음 해 재무 안정성 위험(자본 감소, 부채비율 악화, 현금흐름 악화 등)

### 반드시 지킬 것

- 시간 순서 분할: 과거 연도 학습, 더 최근 연도 검증·홀드아웃
- 미래 재무제표를 과거 feature에 넣지 않는다(데이터 누수 금지)
- 최소 3개 연속 연도 없으면 모델 입력/예측에서 제외
- model version, feature manifest, holdout 성능, calibration 결과를 저장
- 평가 기준 미달 모델은 `READY`로 노출하지 않고 `FINANCIALS_ONLY`를 유지

관련 기존 계획: `docs/superpowers/plans/2026-08-29-dart-company-finance.md`

## 7. 현재 코드/DB 구현 현황

### 완료된 기반

- DART 법인목록 ZIP 다운로드 및 XML 파싱
- `dart_corporations` 적재
- 회사명 정규화와 정확 일치 매칭
- 기존 1,727개 고유 기업 백필 및 집계 로그
- DART CFS 연간 재무제표 JSON 핵심 계정 파싱
- 재무분석 DB 테이블
- 확정 법인 최근 완료 연도 CFS 수집 서비스
- 공고 상세 재무분석 읽기 API

### DB 마이그레이션

파일: `backend/src/main/resources/db/migration/V47__dart_company_finance.sql`

테이블:

- `dart_corporations`
- `company_dart_matches`
- `company_financial_years`
- `company_financial_metrics`
- `company_hiring_monthly_metrics`
- `company_growth_predictions`

주의: 처음 DART 마이그레이션을 V41로 추가했지만 기존 `V41__drop_retired_portfolio_documents.sql`과 충돌해 Flyway가 백엔드를 재시작 루프로 만들었다. **반드시 V47을 사용**하며 새 작업 전 최신 main의 최대 Flyway 번호를 확인한다.

### 현재 API

```http
GET /api/v1/job-postings/{id}/company-finance
```

현재는 DB에 저장된 사실만 읽고 실시간 DART를 호출하지 않는다. 반환 상태는 매칭/재무 연도 수에 따라 `UNMATCHED`, `FINANCIALS_NOT_FOUND`, `DATA_INSUFFICIENT`, `FINANCIALS_ONLY`다. ML 저장 결과와 UI는 아직 미완성이다.

## 8. 브랜치/커밋 이력

초기 DART 기반은 PR #169로 main에 병합된 것으로 배포 로그에서 확인됐다.

| 브랜치 | 주요 커밋 | 의미 |
| --- | --- | --- |
| `codex/dart-flyway-migration-fix` | `fe7bee5` | V41 충돌을 V47로 복구 |
| `codex/dart-company-finance-detail` | `77445b9` | 재무 동기화 + 분석 서비스 |
| 같은 브랜치 | `aa2aa3c` | 상세 재무분석 REST API |
| 같은 브랜치 | `d701048`, `6263088` | 인수인계/실측 보고서 |
| 같은 브랜치 | `ebe7e7e` | 후보 검수 설계 |

최신 비교/PR URL:

`https://github.com/hanyeong0605-prog/Final_Project/compare/main...codex%2Fdart-company-finance-detail?expand=1`

사용자가 기준으로 언급한 브랜치는 `김한영브뤤취학원`이었다. 배포 워크플로는 main을 사용한 정황이 있으므로 병합 대상은 항상 최신 배포 설정을 확인한다.

## 9. 서버 환경변수와 배포

서버 환경파일: `/etc/jobpilot/jobpilot.env`

필수 키(값은 작성하지 않음):

```ini
DART_API_KEY=...
VITE_KAKAO_MAP_KEY=...
VITE_TOSS_CLIENT_KEY=...
DART_BACKFILL_ON_START=false
DART_FINANCIAL_SYNC_ON_START=false
```

`DART_BACKFILL_ON_START=true` 의미:

- 기존 공고의 고유 회사를 매칭하는 일회성 초기 백필 스위치
- 2026-08-29에 두 번 실행됐고 같은 결과를 냈다.
- 이제 `false`로 유지한다.

`DART_FINANCIAL_SYNC_ON_START=true` 의미:

- 상세 기능 브랜치 병합 뒤, `DART_BACKFILL_ON_START=true`와 함께 한 번 켜면 확정 법인의 최근 3개 완료 연도 CFS를 저장한다.
- 대량 DART 호출이므로 재무 3개년 확보율을 측정할 최초 1회에만 켠다.
- 완료 후 두 스위치를 모두 `false`로 되돌린다.

배포 명령:

```bash
cd /opt/jobpilot/jobpilot-ai
git pull
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml \
  up -d --build backend frontend
```

`--env-file`은 frontend 빌드 시 `VITE_KAKAO_MAP_KEY`, `VITE_TOSS_CLIENT_KEY` 변수 치환에 필요하다.

로그 확인:

```bash
sudo env ENV_FILE=/etc/jobpilot/jobpilot.env docker compose \
  --env-file /etc/jobpilot/jobpilot.env \
  -f docker-compose.prod.yml logs --since 60m backend | grep "DART"
```

## 10. 다음 실행 순서

1. 최신 기능 브랜치 PR을 병합하고 배포한다.
2. `DART_BACKFILL_ON_START=true`, `DART_FINANCIAL_SYNC_ON_START=true`를 **한 번만** 설정해 배포한다.
3. `DART financial sync complete: storedAnnualStatements=...` 로그를 확보한다.
4. 두 스위치를 `false`로 되돌리고 재배포한다.
5. 494개 확정 법인 중 재무 3개년 이상 확보 기업 수/비율을 SQL 또는 관리자 프로브로 측정한다.
6. 그 표본 수를 보고 ML 학습 가능 범위와 평가 기준을 확정한다.
7. 후보 검수 DB/API/별칭 재사용을 구현한다.
8. UI 섹션과 그래프를 구현한다.
9. ML 학습·예측 저장·`READY` 화면을 구현한다.

## 11. 남은 구현 상세

### 데이터 수집

- CFS가 없을 때 OFS(별도재무제표) fallback
- `rcept_no`, 통화, 보고서 출처 정확 저장
- 재무 지표 계산 서비스
- 06시 크롤링이 완료된 뒤 새롭거나 오래된 고유 회사만 증분 처리
- 공고마다 DART API를 호출하지 않는다.

### 후보 검수

- `company_dart_match_reviews` 테이블
- 관리자 후보 목록/승인/제외 API
- 승인된 연결을 동일 원본 회사 식별자에 재사용
- 승인 근거와 검수자/시각 보관

### 프론트엔드

- `frontend/src/features/company-finance/` 모듈 생성
- `JobPostingDetailPage.tsx` 내부에 anchor 버튼과 섹션 삽입
- `.company-finance-*`로 CSS 범위 제한, 기존 사용자 수정 충돌 금지
- Lucide 아이콘과 기존 카드 스타일 사용
- 데이터 없음에는 0 값 그래프를 만들지 않음

### ML/AI 서버

- `ai-server/ml/company_finance_dataset.py`
- `ai-server/ml/train_company_growth_model.py`
- time split, holdout, calibration, joblib artifact
- 내부 예측 API와 Spring 검증/저장
- 모델 성능/버전 없는 예측은 사용자 노출 금지

## 12. 테스트와 작업 주의

- 새 기능은 TDD로 실패 테스트 → 최소 구현 → 통과 테스트 순서
- DART 관련 기존 테스트: parser, URI, ZIP parser, matching, sync, migration, analysis service/controller
- Maven 전체 테스트는 당시 종료코드 0이었다. Mockito 동적 agent 경고와 기존 모듈 로그 경고는 존재한다.
- 기존 워크트리는 다른 팀원의 수정이 많다. 격리 worktree를 사용하고 `git reset --hard`, 무관한 파일 삭제/포맷팅을 하지 않는다.
- 실제 API 키·토큰을 출력하지 않는다.

## 13. 관련 문서

- `docs/DART_FINANCE_HOME_HANDOFF.md`: 실행 중심 인수인계
- `docs/company-finance-validation-report.md`: 1차 매칭 실측 보고서
- `docs/superpowers/specs/2026-08-29-dart-company-finance-design.md`: 전체 재무분석 설계
- `docs/superpowers/plans/2026-08-29-dart-company-finance.md`: 기존 단계별 구현 계획
- `docs/superpowers/specs/2026-08-29-dart-candidate-verification-design.md`: 후보 검수/별칭 설계
