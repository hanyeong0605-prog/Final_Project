# 모의면접 - 이력(타임라인) & 크레딧/결제 테이블 설계 (초안, 상의용)

아직 마이그레이션/엔티티 코드는 안 만들었다 - 팀 상의 후 확정되면 그때 착수.
`Member` 테이블은 건드리지 않고, 새 `interview` 도메인(백엔드 `domain/interview/...`)에
전부 둔다는 전제로 설계함.

## 1. `interview_attempts` - 답변 시도 이력 (타임라인의 본체)

질문 하나 + 답변 하나가 한 행. 개인별 타임라인("지난번엔 이랬는데 이번엔...")의 기반
데이터이면서, 동시에 무료 한도 계산의 근거 데이터로도 쓴다(아래 3번).

| 컬럼 | 타입 | 역할 |
|---|---|---|
| `id` | BIGINT PK | |
| `member_id` | BIGINT FK -> members.id | 누구의 시도인지 |
| `question` | TEXT | 그때 보여준 질문 |
| `used_ai_generation` | BOOLEAN | 이 질문이 실제 모델 호출로 생성됐는지(true) vs 자기소개 고정문구/폴백 질문(false)인지. 무료 한도는 이 값이 true인 행만 센다 |
| `transcript` | TEXT, NULL 허용 | STT 인식 결과 |
| `low_confidence_transcript` | BOOLEAN | STT 확신도 낮음 플래그 (지금 `/analyze-answer` 응답과 동일) |
| `voice_metrics_json` | JSON | speaking_rate, pitch_mean_hz 등 음성 지표 스냅샷 (VoiceMetrics.to_dict() 그대로) |
| `face_metrics_json` | JSON, NULL 허용 | 얼굴 지표 스냅샷 (카메라 안 썼으면 NULL) |
| `report` | TEXT, NULL 허용 | Gemini 종합 평가 - "종합 평가 보기"를 안 눌렀으면 NULL로 남음 |
| `report_requested_at` | DATETIME, NULL 허용 | 리포트를 실제로 요청한 시각(=Gemini 호출 시각). 리포트 호출 비용을 질문 생성과 분리해서 추적하고 싶을 때 씀 |
| `created_at` | DATETIME | |

**메모**: 지표를 개별 컬럼으로 쪼갤지(voice_metrics를 speaking_rate_chars_per_min, pitch_mean_hz... 개별 컬럼으로) JSON 컬럼 하나로 뭉칠지 고민 지점. 개별 컬럼은 "지난 세션 평균 말속도" 같은 집계 쿼리가 쉽고, JSON은 `audio_analysis.py`에 지표가 추가/변경돼도 마이그레이션이 필요 없음(유연함). MySQL은 JSON 타입/함수를 지원하니 둘 다 가능 - 초반엔 JSON으로 시작하고, 나중에 "평균 대비 이번엔 어땠는지" 같은 집계 기능이 실제로 필요해지면 그때 필요한 지표만 개별 컬럼으로 뽑아내는 것도 방법.

## 2. 타임라인 조회

새 테이블/쿼리 필요 없음 - `SELECT * FROM interview_attempts WHERE member_id = ? ORDER BY created_at DESC`가 곧 "지난 세션들"이다.

## 3. 무료 한도(질문 생성) 계산

별도 카운터 컬럼/테이블 없이 파생값으로 계산:

```sql
SELECT COUNT(*) FROM interview_attempts
WHERE member_id = ? AND used_ai_generation = true;
```

이 값이 한도(예: 3)를 넘으면 이후엔 서버가 모델 호출 없이 폴백 질문을 내려주고
`used_ai_generation=false`로 기록. 결제해서 크레딧이 생기면 다시 `used_ai_generation=true`
경로를 열어주는 식.

## 4. 결제/크레딧 기능까지 갈 경우 추가되는 테이블

### 4-1. `credit_balances` - 회원별 보유 크레딧 (1:1)

| 컬럼 | 타입 | 역할 |
|---|---|---|
| `member_id` | BIGINT PK, FK -> members.id | |
| `balance` | INT | 현재 보유 크레딧 수 |
| `updated_at` | DATETIME | |

### 4-2. `credit_transactions` - 크레딧 증감 이력 (append-only 원장)

잔액(`credit_balances.balance`)은 매번 다시 계산 안 하고 이 테이블에 쌓이는 걸 반영해서
갱신 - 대신 감사/문의 대응용으로 "언제 왜 얼마나 늘고 줄었는지" 기록이 남아야 해서 필요.

| 컬럼 | 타입 | 역할 |
|---|---|---|
| `id` | BIGINT PK | |
| `member_id` | BIGINT FK | |
| `type` | ENUM(PURCHASE, CONSUME, REFUND, GRANT) | 충전/소비/환불/프로모션 지급 등 |
| `amount` | INT | 증감량(부호로 방향 표현하거나 type과 결합) |
| `balance_after` | INT | 처리 직후 잔액 스냅샷(검증/디버깅용) |
| `related_interview_attempt_id` | BIGINT, NULL 허용, FK -> interview_attempts.id | CONSUME일 때 어떤 시도 때문에 차감됐는지 |
| `related_payment_id` | BIGINT, NULL 허용, FK -> payments.id | PURCHASE일 때 어떤 결제 건인지 |
| `created_at` | DATETIME | |

### 4-3. `payments` - 실제 PG 결제 내역

| 컬럼 | 타입 | 역할 |
|---|---|---|
| `id` | BIGINT PK | |
| `member_id` | BIGINT FK | |
| `pg_provider` | VARCHAR | 예: "toss" |
| `order_id` | VARCHAR, UNIQUE | 결제 요청 시점에 우리 쪽에서 미리 발급하는 주문번호(PG 승인 API 호출 시 필요) |
| `pg_payment_key` | VARCHAR, NULL 허용 | PG사가 발급하는 결제 고유키(승인 완료 후 채워짐) |
| `amount` | INT | 결제 금액(원) |
| `credit_amount` | INT | 이 결제로 지급되는 크레딧 수 |
| `status` | ENUM(PENDING, PAID, FAILED, CANCELED, REFUNDED) | |
| `requested_at` | DATETIME | 결제 요청 시각 |
| `approved_at` | DATETIME, NULL 허용 | PG 승인 완료 시각 |
| `canceled_at` | DATETIME, NULL 허용 | 취소/환불 시각 |

**흐름**: 결제 요청 시 `payments`에 PENDING 행 생성 → PG 승인 콜백에서 `status=PAID`,
`approved_at` 채움 → 동시에 `credit_transactions`에 PURCHASE 행 추가 + `credit_balances.balance`
증가. 질문 생성 시 크레딧 소비는 `credit_transactions`에 CONSUME 행 추가(관련
`interview_attempts` 행과 연결) + `credit_balances.balance` 감소.

## 5. 정리 - 지금 당장 vs 나중

- **지금 필요 (타임라인 + 무료 3개 한도)**: `interview_attempts` 하나면 됨.
- **나중에 결제 붙일 때 추가**: `credit_balances`, `credit_transactions`, `payments` 세 개.
- `Member` 테이블은 어느 경우든 안 건드림.
