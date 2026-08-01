# V1 API 계약 초안

모든 API는 `/api/v1` 아래에 두고, 인증이 필요한 요청에는 `Authorization: Bearer <access-token>`을 보낸다. `sourceUrl`은 반드시 원문 공고·기회 페이지여야 한다.

## 채용공고 수집

### SaraminDATA 동기화

```http
POST /api/v1/providers/saramin-data/sync
```

`SARAMIN_DATA_ENABLED=true`와 `SARAMIN_ACCESS_KEY`가 설정된 서버에서만 실행한다. 사람인 API 응답을
`SaraminDATA` 경계 안에서 검증하고, `externalJobId=사람인 공고 ID` 기준으로
`job_postings`에 upsert한다. `SARAMIN_CRAWL_ENABLED=true`이면 API가 제공한 사람인 HTTPS 원문 URL만
낮은 빈도로 보완 수집하며, 실패해도 API 데이터 저장은 계속한다.

```json
{
  "provider": "SARAMIN_DATA",
  "fetched": 50,
  "created": 35,
  "updated": 13,
  "skipped": 1,
  "failed": 1
}
```

API 키, HTML 원문, 사람인 전용 DTO를 프론트엔드 응답에 노출하지 않는다.

## 회원 입력 데이터

회원가입 후 회원 ID에 다음 데이터가 연결된다.

- `member_profiles`: 희망 IT 직무·지역·지원 가능 시점
- `member_specifications`: 학력·전공·경력 개월·기술 요약·포트폴리오
- `member_skills`, `projects`, `certificates`, `education_histories`: 비교 가능한 구조화 근거
- `self_introductions`: 여러 자소서 버전과 대표 자소서

## 맞춤 채용공고

### 추천 목록

```http
GET /api/v1/job-matches?memberId=1&level=APPLY_NOW
```

추천 단계는 `APPLY_NOW`, `CHALLENGE_AFTER_GAPS`, `DIFFICULT_NOW` 세 가지다. 합격 확률이 아니라 사람인 공고의 필수 요건과 회원이 입력한 근거 사이의 준비 상태다.

```json
{
  "content": [
    {
      "jobPostingId": 101,
      "companyName": "모노랩",
      "title": "신입 백엔드 개발자 (Java/Spring)",
      "source": "사람인",
      "sourceUrl": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=101",
      "deadlineAt": "2026-08-12T23:59:59+09:00",
      "recommendationLevel": "APPLY_NOW",
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
  "recommendationLevel": "APPLY_NOW",
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
