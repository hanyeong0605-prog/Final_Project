# 코드 구조 규칙

## 프론트엔드

- `pages`: URL에 대응하는 화면. 도메인 컴포넌트를 조합하고 페이지 단위 상태만 둔다.
- `features/<domain>/api`: 해당 도메인 API 요청과 응답 변환만 둔다.
- `features/<domain>/model`: DTO, 화면 모델, 상수, 상태 Context를 둔다.
- `features/<domain>/components`: 해당 도메인에만 의미가 있는 UI를 둔다.
- `features/<domain>/data`: API 미연동 개발용 fixture. 화면에서 직접 import하지 않는다.
- `shared`: 여러 도메인에서 재사용되는 UI와 상수만 둔다.

예: 채용공고 화면은 `pages/JobMatchesPage.tsx`가 렌더링하고, 목록 조회는 `features/jobs/api/jobMatchesApi.ts`, 타입은 `features/jobs/model/job.types.ts`, 카드는 `features/jobs/components/JobCard.tsx`가 맡는다.

## 백엔드

```text
domain/matching
├─ controller  # HTTP 요청·응답 경계, 인증 주체 전달
├─ service     # 유스케이스와 트랜잭션 경계
├─ repository  # DB 조회/저장
├─ entity      # JPA 영속 모델
├─ dto         # API 전용 request/response
└─ policy      # 독립적으로 검증 가능한 매칭 규칙
```

- Controller는 Entity를 직접 반환하지 않는다.
- Service는 DTO 조립과 여러 Repository 조합을 담당한다.
- Repository는 단일 aggregate의 조회/저장에만 집중한다.
- `global`에는 예외 처리, 보안, 공통 응답 등 도메인 외 횡단 관심사만 둔다.
- 다음 도메인(`member`, `opportunity`, `planner`)도 `domain/<name>/controller|service|repository|entity|dto` 규칙을 동일하게 적용한다.
