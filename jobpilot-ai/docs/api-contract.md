# V1 API 계약 초안

모든 API는 `/api/v1` 아래에 두고, 인증이 필요한 요청에는 `Authorization: Bearer <access-token>`을 보낸다. `sourceUrl`은 반드시 원문 공고·기회 페이지여야 한다.

## 채용공고 수집

### 제공처 공고 동기화

```http
POST /providers/work24/sync
```

운영자 또는 스케줄러 전용 API다. 외부 응답을 곧바로 저장하지 않고 반드시 `NormalizedJobPosting`으로 변환한 뒤, `(sourceId, externalJobId)` 기준으로 upsert한다.

```json
{
  "provider": "WORK24",
  "fetched": 120,
  "created": 25,
  "updated": 93,
  "skipped": 2
}
```

### 사용자 공고 직접 등록

```http
POST /job-postings/manual
```

```json
{
  "sourceUrl": "https://example.com/jobs/123",
  "title": "주니어 백엔드 개발자",
  "companyName": "예시회사",
  "description": "사용자가 확인한 공고 전문 또는 허용된 범위의 내용",
  "deadlineAt": "2026-08-20T23:59:59+09:00"
}
```

`MANUAL` source의 UUID 외부 ID를 생성한다. 잡코리아 등 특정 제공처의 수집 방식은 사용 권한·계약·robots 정책을 확인한 뒤 Provider로 추가한다. 그 전에는 이 직접 등록 경로를 제공한다.

## 맞춤 채용공고

### 추천 목록

```http
GET /job-matches?memberId=1&grade=READY_TO_APPLY
```

기본 목록에는 `READY_TO_APPLY`, `NEEDS_IMPROVEMENT`만 반환한다. `INSUFFICIENT_EVIDENCE`는 사용자가 명시적으로 필터를 선택한 경우에만 포함한다.

```json
{
  "content": [
    {
      "jobPostingId": 101,
      "companyName": "모노랩",
      "title": "신입 백엔드 개발자 (Java/Spring)",
      "source": { "code": "WORK24", "displayName": "고용24" },
      "sourceUrl": "https://source.example/jobs/101",
      "deadlineAt": "2026-08-12T23:59:59+09:00",
      "grade": "READY_TO_APPLY",
      "readinessScore": 86.0,
      "summaryComment": "Spring Boot·JPA·MySQL 프로젝트 근거가 필수 요건과 직접 연결됩니다."
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### 공고별 근거 매트릭스

```http
GET /job-matches/{jobPostingId}?memberId=1
```

```json
{
  "jobPostingId": 101,
  "grade": "READY_TO_APPLY",
  "readinessScore": 86.0,
  "evidences": [
    {
      "requirement": "Spring Boot 기반 API 개발",
      "requirementType": "REQUIRED",
      "sourceExcerpt": "Spring Boot 기반 REST API 개발 경험",
      "status": "DIRECT",
      "memberEvidence": {
        "type": "PROJECT",
        "id": 44,
        "title": "MealMate 예약 API 구현"
      },
      "comment": "프로젝트에서 Spring Boot API 구현 경험이 확인됩니다.",
      "nextAction": "README에 담당 API와 트러블슈팅 링크를 추가하세요."
    }
  ]
}
```

현재 `memberId` 파라미터는 JWT 이전 개발 단계의 임시 경계입니다. Spring Security 적용 후에는 controller가 principal에서 회원 ID를 가져오고, service·repository·dto 구조는 유지합니다.

## 추천 기회와 플래너

### 보완 기회

```http
GET /opportunities/recommended?types=TRAINING,CERTIFICATION,CONTEST
```

기회는 부족한 `skills`와 `opportunity_skills`의 교집합, 마감 가능 여부, 사용자의 직무를 기준으로 정렬한다. 공고의 필수 자격으로 명시되지 않은 자격증을 ‘필수’처럼 표현하지 않는다.

### 관심 등록/해제

```http
POST /interests
DELETE /interests/{targetType}/{targetId}
```

```json
{ "targetType": "JOB_POSTING", "targetId": 101 }
```

등록은 하나의 트랜잭션으로 `user_interests`와 `planner_events`를 만든다.

- `JOB_POSTING`: 지원 마감 이벤트
- `TRAINING`: 신청 마감, 교육 시작/종료 이벤트
- `CERTIFICATION`: 원서 마감, 시험일, 합격 발표일 이벤트
- `CONTEST`: 접수 마감, 결과 발표 이벤트

### 플래너

```http
GET /planner-events?from=2026-08-01&to=2026-08-31
```

일정을 사용자가 삭제해도 해당 공고·기회나 매칭 이력을 삭제하지 않는다. 일정의 사용자 삭제 상태만 별도로 기록한다.
