# DART 후보 법인 검증 설계

## 목표

`CANDIDATE` 회사명을 자동으로 재무제표와 연결하지 않고, 관리자 승인으로 만든 검증 별칭만 이후 공고에서 `CONFIRMED`로 재사용한다.

## 원칙

- 부분 일치·유사도만으로는 절대 `CONFIRMED`가 되지 않는다.
- 승인 근거와 승인 시각을 남긴다.
- 후보 제외도 저장해 같은 잘못된 후보가 반복 노출되지 않게 한다.
- 06시 크롤링으로 새 공고가 들어와도 회사별로 한 번만 별칭을 재사용한다.
- 사용자 상세 화면에는 `CONFIRMED` 회사만 재무 분석을 노출한다.

## 데이터 모델

`company_dart_match_reviews`

| 열 | 의미 |
| --- | --- |
| source_provider, source_company_id, normalized_company_name | 원본 회사 식별 |
| proposed_corp_code | 검토 대상 DART 법인 |
| review_status | `APPROVED` 또는 `REJECTED` |
| evidence_type | `ADMIN_REVIEW`, `OFFICIAL_DOMAIN`, `BUSINESS_NUMBER` 등 |
| evidence_note | 관리자가 남긴 짧은 근거 |
| reviewed_by, reviewed_at | 검수 이력 |

`APPROVED` 행은 매칭 전에 조회한다. 원본 식별자가 같으면 해당 `corp_code`를 `CONFIRMED`로 저장한다. `REJECTED`는 후보 목록에서 숨기되, 다른 법인 후보가 생길 가능성은 막지 않는다.

## 관리자 흐름

1. 관리자는 후보 목록에서 원티드 회사명, 정규화명, 제안 DART 법인명/코드, 공고 URL을 확인한다.
2. 공식 홈페이지·사업자 정보 등 외부 근거를 확인한다.
3. 승인하면 `APPROVED` 리뷰와 `CONFIRMED` 매칭을 함께 저장한다.
4. 제외하면 `REJECTED` 리뷰를 저장하고 재무 수집 대상에서 제외한다.
5. 다음 크롤링부터 같은 회사 식별자는 승인된 연결을 즉시 재사용한다.

## API

- `GET /api/v1/admin/company-finance/candidates`: 후보 목록, 페이지네이션
- `POST /api/v1/admin/company-finance/candidates/{matchId}/approve`: 대상 corp code·근거를 받아 승인
- `POST /api/v1/admin/company-finance/candidates/{matchId}/reject`: 근거를 받아 제외

모든 API는 기존 관리자 권한 정책을 사용한다. 일반 사용자는 호출할 수 없다.

## 측정

- 후보 총수, 승인 수, 제외 수, 대기 수
- 승인 후 재무제표 3개년 확보 수
- 자동 재사용된 승인 별칭 수

## 비범위

- 후보 929개 전체를 자동 확정하는 AI/문자열 유사도 모델
- 제3자 기업정보 유료 계약
- 사용자에게 후보 상태를 노출하는 별도 화면
