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

## 6. 무료/유료 기능 차별점 (2026-08-07)

LoRA 재학습 모델의 품질이 분야 구분을 안정적으로 못 해낸다는 걸 `ml/test_field_questions.py`로
직접 확인한 뒤(풀스택인데 OpenCL/GPU 질문이 나오는 등), 무료/유료 차별화 축을 "직접 학습시킨
모델 vs Gemini"가 아니라 **Gemini 호출의 컨텍스트 깊이/횟수**로 잡기로 정리함. LoRA는
production 라우팅에서 이미 폴백으로 격하돼 있음(`router.py` - Gemini 우선, 실패 시에만 LoRA).

| # | 항목 | 무료 | 유료 |
|---|---|---|---|
| 1 | 개인화 깊이 | job/tech_summary 정도만 프롬프트에 반영 | 이력서·자소서 원문을 프롬프트에 통째로 넣거나(long-context), 7번 RAG로 실제 기출 질문을 검색해 근거로 삼아 생성 |
| 2 | 질문/세션 횟수 | 하루 체험 세션 1~2회 또는 질문 수 제한(`interview_attempts.used_ai_generation=true` 카운트 기준, 3번 섹션 로직 그대로 재사용) | 무제한(크레딧 소진까지) |
| 3 | 리포트 깊이 | 종합 평가 텍스트만 | 질문별 모범답안 + 약점 카테고리 딥링크(태스크 #39) + 세션 히스토리 기반 성장 추이 |
| 4 | 꼬리질문(멀티턴) | 없음 - 세션당 고정 질문 수만 | 답변 직후 즉석 후속 질문 생성 (세션당 Gemini 호출 횟수가 늘어나서 크레딧 소비량도 커짐) |
| 5 | 학습실/보완 플랜 | 없음 | 리포트에서 드러난 약한 기술을 기반으로 학습 커리큘럼 제안 |

크레딧 소비량은 기능마다 균일하지 않음 - 1번(이력서+RAG 컨텍스트)과 4번(멀티턴)은 토큰이 훨씬
많이 나가니까, `credit_transactions.amount`를 기능별로 다르게 책정하는 걸 전제로 설계해야 함
(예: 기본 질문 생성 1크레딧, RAG+이력서 개인화 질문 3크레딧, 꼬리질문 2크레딧 등 - 정확한 배율은
실제 토큰 사용량 측정 후 확정).

## 7. RAG 기반 개인 맞춤형 질문 생성 설계 (2026-08-07)

**용어 정리**: 이력서/자소서 원문을 프롬프트에 통째로 넣는 건 RAG가 아니라 long-context
prompting임(검색 단계가 없음). RAG가 실제로 맞는 지점은 따로 있음 - 아래 참고.

**아이디어**: LoRA 학습용으로 만들었던 `ml/interview_qa_pairs*.jsonl`(수백 개 실제 기출/생성
질문, 분야·카테고리 태깅 완료)을 폐기하지 않고 **RAG 지식 베이스로 재활용**한다. 파인튜닝
가중치는 품질 미흡으로 production에서 안 쓰지만, 그 밑에 깔린 데이터셋 자체는 그대로 자산으로
남는 셈 - 학습 파이프라인이 헛수고가 아니게 됨.

**흐름**:
1. 회원이 job/category/tech_summary(+ 유료면 이력서 요약)를 선택/입력
2. 그 조건을 쿼리로 임베딩 생성
3. `interview_qa_pairs*.jsonl` 질문들의 임베딩과 코사인 유사도 비교 - top-k(예: 3~5개) 유사
   질문 검색
4. 검색된 실제 질문들을 few-shot 예시로 Gemini 프롬프트에 삽입: "아래는 참고할 실제 기출
   질문들이다: [검색 결과] 이 스타일/난이도를 참고해서 이 지원자 정보에 맞는 새 질문을 만들어라"
5. Gemini가 검색된 예시에 근거해 새 질문 생성 - LoRA처럼 없는 개념을 지어내는(OpenCL 예시처럼)
   문제를 줄임

**인프라**: 데이터 규모가 수백~수천 개 수준이라 별도 벡터DB(Pinecone 등) 없이 임베딩을 MySQL에
저장해두고 요청 시점에 코사인 유사도를 직접 계산해도 충분함(필요하면 로컬 FAISS 인덱스 정도로
확장). 무료 티어에서 비용 없이 운영 가능한 규모.

**티어 구분**: RAG 검색+few-shot 자체는 가벼우니 무료에도 넣을 수 있지만, 이력서 long-context와
결합해서 진짜 "내 스펙에 맞는 기출 스타일 질문"을 만드는 조합은 유료로 묶는다(6번 섹션 1번 항목).

**LoRA 모델의 최종 위치**: 코드(`question_generator_lora/` 어댑터, 학습 노트북)는 삭제하지
않고 유지 - production에서는 Gemini 실패 시 폴백으로만 호출되고, 포트폴리오/면접에서는 "데이터
파이프라인 구축 → LoRA 파인튜닝 → 실측 품질 한계 발견 → 그 데이터셋을 RAG 지식 베이스로
전환"이라는 하나의 완결된 엔지니어링 스토리로 설명 가능.
