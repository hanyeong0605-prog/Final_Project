# JobPilot-AI 아키텍처 & API 레퍼런스

> 다이어그램은 [jobpilot-ai-architecture.drawio](./jobpilot-ai-architecture.drawio) 참고 (draw.io/diagrams.net에서 열기).
> 이 문서는 실제 코드(Spring Security 설정, 컨트롤러, FastAPI 라우터)를 근거로 작성했습니다.

---

## 1. 전체 구성

| 서비스 | 위치 | 기술 스택 | 포트 |
|---|---|---|---|
| Frontend | `jobpilot-ai/frontend` | React 18 + TypeScript (Vite), Nginx로 정적 서빙 | 80 (컨테이너 내부) |
| Backend | `jobpilot-ai/backend` | Spring Boot 3.4 / Java 21 | 9000 |
| AI Server | `jobpilot-ai/ai-server` | FastAPI (Python) | 8001 |
| Wordcloud/얼굴인증 서버 | `jobpilot-ai/ml-project` | FastAPI (Python) | 8000 |
| DB | MySQL 8.4 | - | 3306 |
| (예정) 패스워드리스 인증 서버 | 신규 EC2 | 미정 | 8080 / 11040 / 15010(WS) |

운영 환경은 EC2 한 대에서 Docker Compose로 `frontend / backend / ai-server / wordcloud-server(ml-project)` 4개 컨테이너 + 호스트 Nginx(TLS 종료)로 구성됩니다. `frontend` 컨테이너 내부 Nginx가 경로별로 나머지 3개 서비스에 리버스 프록시합니다 (`frontend/nginx.conf`):

```
/api/, /oauth2/, /login/oauth2/, /ws/   → backend:9000
/ai-api/*  (prefix strip)               → ai-server:8001
/wordcloud-api/* (prefix strip)         → wordcloud-server:8000
```

---

## 2. 전체 요청 흐름 (한 번 훑기)

1. 브라우저 → **Host Nginx**(EC2, TLS 종료, HTTPS) → **Frontend 컨테이너**(내부 Nginx)로 프록시
2. Frontend 컨테이너는 정적 React 빌드(`dist/`)를 서빙하면서, API 요청은 경로 규칙에 따라 backend/ai-server/wordcloud-server로 전달
3. **Backend(Spring Boot)**가 사실상 메인 게이트웨이 역할 — 대부분의 도메인 API(회원, 이력서, 채용공고, 매칭, 결제 등)를 직접 처리하고, AI 연산이 필요한 부분(모의면접 STT/질문생성, 이력서 AI 분석, 채용공고 매칭 점수 등)은 **서버 간 호출**로 ai-server(`AI_SERVER_BASE_URL`)에 위임
4. Backend는 관리자 얼굴 인증이 필요할 때 wordcloud-server(`FACE_VERIFICATION_BASE_URL`)를 호출 (DeepFace)
5. 세 서비스 모두 각자의 드라이버로 같은 **MySQL**에 접근 (Backend: JPA/Flyway, AI Server: SQLAlchemy, Wordcloud: PyMySQL)
6. 외부 연동: Google Gemini(질문 폴리싱·이력서 지원), Google Cloud STT(모의면접 음성인식), Kakao Map(지도), Toss Payments(결제), OAuth2(Google/Kakao/Naver 소셜 로그인), Web Push(VAPID, 마감임박 알림)
7. 서버 간 호출은 대부분 공용 비밀키 `INTERNAL_API_KEY`를 `X-Internal-Api-Key` 헤더로 검증 (사용자 JWT가 없는 서버-서버 요청이기 때문)

---

## 3. 로그인 / 인증 흐름 (상세)

### 3.1 인증 방식 요약

Spring Security는 **완전 무상태(stateless) JWT** 방식입니다. HTTP 세션은 소셜 로그인 리다이렉트 도중 OAuth2 authorization state를 잠깐 들고 있는 용도로만 쓰이고, 실제 API 인증은 전부 `Authorization: Bearer <JWT>` 헤더 + Spring Security의 OAuth2 Resource Server 기능(`NimbusJwtDecoder`, HMAC-SHA256)으로 처리됩니다. 별도의 JWT 필터 클래스를 직접 작성하지 않고 **선언적 설정**으로 처리하는 구조입니다.

계정 종류는 두 가지로 완전히 분리되어 있습니다.

| 구분 | 계정 엔티티 | 로그인 API | JWT 구분 클레임 |
|---|---|---|---|
| 회원(구직자) | `Member` | `/api/v1/auth/login` | (기본, `actorType` 클레임 없음) |
| 기업회원 | `EmployerAccount` | `/api/v1/employer/auth/login` | `actorType=EMPLOYER` |

이 `actorType` 클레임 덕분에 회원 토큰으로 기업 API를, 기업 토큰으로 회원 API를 호출하는 게 원천 차단됩니다 (`AuthenticatedMember`/`AuthenticatedEmployer` 헬퍼가 상호 검증).

**리프레시 토큰은 없습니다.** Access Token 하나만 발급되고 (`app.jwt.access-token-minutes`, 기본 120분), 만료되면 재로그인해야 합니다.

### 3.2 아이디/비밀번호 로그인 (회원)

1. `POST /api/v1/auth/login` — `loginId`+`password` 전송
2. `AuthService.login`이 `Member`를 조회, `PasswordEncoder`(BCrypt)로 비밀번호 검증 → 불일치 시 `InvalidCredentialsException`
3. 성공 시 `JwtTokenService.issue(Member)`가 JWT 발급 — 클레임: `iss`, `iat`, `exp`, `sub`=회원 id, `loginId`, `email`, `role`(`USER`/`ADMIN`)

### 3.3 회원가입 (이메일 인증 필수)

1. `POST /api/v1/auth/email-verifications` — 이메일로 6자리 코드 발송 (Gmail SMTP, 재발송 쿨다운 60초, 유효기간 10분, 해시로 저장)
2. `POST /api/v1/auth/email-verifications/confirm` — 코드 확인(최대 실패 5회) → 성공 시 1회용 `verificationToken` 발급
3. `POST /api/v1/auth/signup` — 위 토큰을 포함해 회원가입 → 토큰 소비(1회성) 후 `Member` 생성

### 3.4 소셜 로그인 (Google / Kakao / Naver)

1. 프론트가 `GET /oauth2/authorization/{google|kakao|naver}`로 리다이렉트 (Spring 기본 진입점)
2. 각 제공자 로그인 후 `GET /login/oauth2/code/{registrationId}`로 콜백
3. `OAuthAuthenticationSuccessHandler` → `OAuthLoginService.begin`:
   - 이미 연동된 `(provider, providerSubject)`가 있으면 → **즉시 로그인 완료**, JWT 발급
   - 제공자가 준 이메일이 기존 회원과 일치하면 → 자동 연동 후 즉시 완료
   - 둘 다 아니면 → `OAuthPendingLogin`(10분 유효) 생성, "pending" 상태로 프론트에 티켓 반환
4. **완료(completed)**: `{성공 리다이렉트 URL}#access_token=<JWT>` 로 리다이렉트 (프론트는 URL 프래그먼트에서 토큰 추출)
5. **대기(pending, 신규 소셜 가입자)**: `.../oauth/complete?ticket=...&provider=...&nickname=...&email=...` 로 리다이렉트
6. 프론트의 `/oauth/complete` 화면에서 `POST /api/v1/auth/oauth/complete` 호출 — 약관 동의, 이메일 확정(제공자가 이메일을 안 줬으면 이메일 인증 절차 재사용), 신규면 `Member` 생성 + `MemberOAuthAccount` 연동 → JWT 발급
7. 로그인 실패 시 `/login?socialError=OAUTH_PROVIDER_FAILED` 또는 `SOCIAL_LOGIN_FAILED`로 리다이렉트

Kakao는 이메일 스코프 미승인 상태라 `profile_nickname`만 사용 — 이메일이 없는 케이스가 위 6번의 "이메일 인증 재사용" 분기로 처리됩니다.

### 3.5 기업 회원(Employer) 인증

- `POST /api/v1/employer/auth/signup`, `POST /api/v1/employer/auth/login` — 회원과 동일한 BCrypt 패턴이지만 완전히 별도 테이블/서비스. 소셜 로그인 없음.

### 3.6 관리자(Admin) — 역할 검사 + 얼굴 인증 2FA

관리자 기능은 **두 겹**으로 보호됩니다.

1. **역할 검사**: `AdminAccessService.requireAdmin`이 매 요청마다 DB에서 `Member.role == ADMIN`을 재확인 (토큰 클레임만 믿지 않음)
2. **휴대폰 얼굴 인증 2FA** (브라우저 세션 단위, `/api/v1/admin/**` 전체에 인터셉터 적용):
   - `POST /api/v1/admin/face-pairings` — PC가 QR용 세션 생성 (2분 유효)
   - 휴대폰이 QR 스캔 → `POST /api/v1/admin/face-pairings/{sessionId}/capture`로 촬영 사진 전송
   - Backend가 `X-Internal-Api-Key`로 **wordcloud-server**의 `POST /api/internal/admin/face/verify` 호출 → DeepFace(VGG-Face + RetinaFace)로 등록된 기준 사진(`ADMIN_FACE_REFERENCE_DIR`)과 대조
   - 검증 성공 시 세션이 `VERIFIED`로 전환 (8시간 유효), PC는 이후 모든 관리자 API 요청에 `X-Admin-Face-Session` 헤더를 실어 보냄
   - 기준 사진 등록/조회: `GET/POST /api/v1/admin/face-references`

### 3.7 서버 간(server-to-server) 인증

사용자 JWT가 없는 서버-서버 호출(ai-server → backend, 등)은 공용 비밀키 방식입니다.
- `InternalApiKeyFilter`가 `X-Internal-Api-Key` 헤더를 `INTERNAL_API_KEY` 환경변수와 비교
- 대상: `POST /api/v1/job-postings/ingest`(크롤링 결과 저장), `crawl-runs/**`, ai-server의 내부 전용 엔드포인트들, wordcloud-server의 얼굴인증 엔드포인트

### 3.8 WebSocket (모의면접 카메라 페어링)

STOMP가 아니라 순수 `WebSocketHandler` 방식입니다.
1. `POST /api/v1/camera-pairings` (JWT 인증) — PC가 `roomId` + QR용 `pairingToken` + PC용 `socketTicket` 발급 (5분 유효)
2. 휴대폰이 QR 스캔 → `POST /api/v1/camera-pairings/join` — 폰용 `socketTicket` 발급 (토큰 1회성 소비)
3. 양쪽 모두 `ws://.../ws/camera-pair?ticket=<socketTicket>`로 연결 — 핸드셰이크 시점엔 `Authorization` 헤더가 없으므로 이 경로는 `permitAll`, 대신 `CameraPairingWebSocketTicketInterceptor`가 1회용 티켓으로 인증
4. 실제 영상/음성은 이 WebSocket을 시그널링(SDP/ICE 교환)으로만 쓰고 P2P(WebRTC)로 직접 전송됨

---

## 4. 전체 API 목록

### 4.1 Backend (Spring Boot, `:9000`)

#### 인증 (`domain.auth`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인, JWT 발급 |
| POST | `/api/v1/auth/oauth/complete` | 소셜 로그인 대기(pending) 완료 |
| GET | `/api/v1/auth/login-id-availability` | 아이디 중복 확인 |
| GET | `/api/v1/auth/me` | 내 프로필 조회 |
| POST | `/api/v1/auth/email-verifications` | 이메일 인증코드 발송 |
| POST | `/api/v1/auth/email-verifications/confirm` | 인증코드 확인 |
| POST | `/api/v1/dev/auth/token` | (local 전용) 더미 회원 토큰 발급 |
| POST | `/api/v1/dev/auth/admin-token` | (local 전용) 더미 관리자 토큰 발급 |

#### 관리자 (`domain.admin`, `domain.Check`)
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/admin/overview` | 대시보드 요약 통계 |
| GET | `/api/v1/admin/members` | 회원 목록/검색 |
| PATCH | `/api/v1/admin/members/{id}/role` | 회원 권한 변경 |
| PATCH | `/api/v1/admin/members/bulk-role` | 회원 권한 일괄 변경 |
| DELETE | `/api/v1/admin/members/{id}` | 회원 삭제 |
| GET | `/api/v1/admin/job-postings` | 채용공고 목록(관리자용) |
| PATCH | `/api/v1/admin/job-postings/{id}/status` | 공고 상태 변경 |
| PATCH | `/api/v1/admin/job-postings/bulk-status` | 공고 상태 일괄 변경 |
| PUT | `/api/v1/admin/job-postings/{id}` | 공고 수정 |
| DELETE | `/api/v1/admin/job-postings/{id}` | 공고 숨김 |
| GET | `/api/v1/admin/employers` | 기업 계정 목록 |
| PATCH | `/api/v1/admin/employers/{id}/approve` | 기업 계정 승인 |
| PATCH | `/api/v1/admin/employers/{id}/reject` | 기업 계정 거절 |
| POST | `/api/v1/admin/face-pairings` | 얼굴인증 QR 세션 생성 |
| POST | `/api/v1/admin/face-pairings/{sessionId}/capture` | 휴대폰 촬영 사진 제출 |
| GET | `/api/v1/admin/face-pairings/{sessionId}` | 인증 결과 폴링 |
| GET | `/api/v1/admin/face-references` | 관리자 기준사진 등록 현황 |
| POST | `/api/v1/admin/face-references/{loginId}` | 기준사진 업로드 |
| POST | `/api/v1/admin/crawl/wanted` | 원티드 크롤링 트리거 |
| GET | `/api/v1/admin/crawl/wanted/status` | 크롤링 상태 확인 |
| POST | `/api/checks/correct` | 맞춤법 검사(Bareun.ai 프록시) |

#### 회원 프로필 (`domain.member`)
| Method | Path | 설명 |
|---|---|---|
| PATCH | `/api/v1/members/me/nickname` | 닉네임 변경 |
| PATCH | `/api/v1/members/me/password` | 비밀번호 변경 |
| DELETE | `/api/v1/members/me` | 회원 탈퇴 |
| GET/PUT/DELETE | `/api/v1/members/me/career-profile` | 커리어 프로필 조회/저장/초기화 |
| POST | `/api/v1/members/me/career-profile/skip` | 온보딩 스킵 |
| GET/PUT | `/api/v1/members/me/certificates` | 보유 자격증 조회/저장 |
| GET/PUT | `/api/v1/members/me/skills` | 보유 스킬 조회/저장 |
| GET/POST/PUT/DELETE | `/api/v1/members/me/projects` | 프로젝트 이력 CRUD |
| GET/POST/PUT/DELETE | `/api/v1/members/me/self-introductions` | 자기소개서 CRUD |
| GET/POST | `/api/v1/members/me/interview-sessions` | 모의면접 이력 조회/저장 |
| GET | `/api/v1/certifications/catalog` 외 5종 | Q-Net 자격증 카탈로그/북마크/추천 |
| GET | `/api/v1/education/schools`, `/api/v1/education/majors` | CareerNet 학교/전공 검색 |
| GET | `/api/v1/skills` | 스킬 카탈로그 검색(자동완성) |

#### 이력서 (`domain.resume`)
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/members/me/resume-documents` | 이력서 문서 목록 |
| POST | `/api/v1/members/me/resume-documents/extract` | 파일 업로드 → 텍스트 추출 |
| POST | `/api/v1/members/me/resume-documents/{id}/apply-profile` | 추출 결과 프로필 반영 |
| PATCH | `/api/v1/members/me/resume-documents/{id}/extraction-review` | 추출 결과 수정 |
| DELETE | `/api/v1/members/me/resume-documents/{id}` | 삭제 |
| POST | `/api/v1/members/me/resume-documents/generate` | AI 이력서 초안 생성 |
| PATCH | `/api/v1/members/me/resume-documents/{id}/title` | 제목 변경 |
| GET | `/api/v1/members/me/resume-documents/{id}/download.docx` | Word 다운로드 |
| GET/PUT | `/api/v1/members/me/resume-save-state` | 작성 중 상태 저장 |
| GET/PUT | `/api/v1/members/me/resume-ai-consent` | AI 사용 동의 설정 |
| GET/POST/PUT/DELETE | `/api/v1/members/me/resume-entries` | 이력 타임라인 CRUD |

#### 모의면접 카메라 페어링
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/camera-pairings` | QR 페어링 세션 생성 |
| POST | `/api/v1/camera-pairings/join` | 폰이 세션 참가 |
| WS | `/ws/camera-pair?ticket=...` | 시그널링 WebSocket |

#### 채용공고 / 매칭 / 지도
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/job-postings` | 채용공고 검색/페이지네이션 |
| GET | `/api/v1/job-postings/{id}` | 공고 상세(조회수 증가) |
| POST | `/api/v1/job-postings/ingest` | (내부) 크롤링 결과 저장 |
| POST | `/api/v1/job-postings/crawl-runs/start`, `/{runId}/complete` | (내부) 크롤 실행 기록 |
| GET | `/api/v1/job-postings/existing` | (내부) 변경분만 크롤링하도록 기존 목록 제공 |
| POST | `/api/v1/job-postings/requirement-extractions` | 공고 요건 AI 추출 |
| POST/GET | `/api/v1/job-postings/requirement-extractions/backfill` | 기존 공고 일괄 추출 트리거/상태 |
| POST | `/api/v1/providers/saramin-data/sync` | 사람인 오픈API 동기화 |
| GET | `/api/location-jobs` | 반경 내 지도 기반 채용공고 |
| GET | `/api/v1/job-matches` | 내 매칭 결과 목록 |
| GET | `/api/v1/job-matches/{id}` | 매칭 상세/근거 |
| POST | `/api/v1/job-matches/{id}/refresh-evidence` | 매칭 근거 재생성 |
| GET | `/api/v1/job-matches/{id}/growth-actions` | 갭 해소용 추천 액션 |
| POST | `/api/v1/job-matches/recalculate` | 전체 매칭 재계산 |
| POST | `/api/v1/job-matches/model/retrain` | 추천 모델 재학습 트리거 |

#### 관심공고 / 도서 / 진로테스트 / 플래너 / 구독 / 알림
| Method | Path | 설명 |
|---|---|---|
| GET/POST | `/api/v1/interests` | 북마크 조회/토글 |
| GET | `/api/v1/opportunities/recommended`, `/bookmarked`, `/{id}` | 대외활동/공고 |
| GET | `/api/v1/books` | 알라딘 도서 추천 검색 |
| GET | `/api/tests/questions/{q}`, POST `/api/tests/report` | 커리어넷 진로심리검사 프록시 |
| GET/POST/PUT/DELETE | `/api/v1/planner-events` | 일정 CRUD |
| GET | `/api/v1/members/me/subscription`, `/plan` | 구독 상태/요금제 |
| POST | `/.../checkout`, `/confirm`, `/cancel` | Toss Payments 결제 흐름 |
| GET | `/api/v1/notifications`, `/unread-count` | 알림 목록/미읽음 수 |
| POST | `/.../{id}/read`, `/read-all` | 알림 읽음 처리 |
| GET | `/api/v1/push/vapid-public-key` | VAPID 공개키 |
| POST | `/api/v1/push/subscribe`, `/unsubscribe`, `/test-send` | 푸시 구독 관리 |

#### 기업(Employer)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/employer/auth/signup`, `/login` | 기업 가입/로그인 |
| GET | `/api/v1/employer/auth/me` | 내 기업 프로필 |
| GET/POST/PUT/DELETE | `/api/v1/employer/job-postings` | 자사 공고 CRUD |

#### 기타
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/health` | 헬스체크 |

### 4.2 AI Server (FastAPI, `:8001`)

| Router | Method | Path | 설명 |
|---|---|---|---|
| crawler | POST | `/crawler/wanted/run` | 원티드 크롤링 실행 (백그라운드 시 내부키 필요) |
| crawler | GET | `/crawler/wanted/status` | 크롤링 상태 조회 |
| interview | POST | `/interview/next-question` | 다음 면접 질문 생성 (`mode=practice`면 코퍼스만, `mode=real`이면 Gemini+RAG - mock-interview-tech-reference.md 참고) |
| interview | POST | `/interview/analyze-answer` | 음성 업로드 → STT + 음성 특징 분석 |
| interview | POST | `/interview/evaluate` | 단일 문답 AI 평가 |
| interview | POST | `/interview/evaluate-session` | 세션 전체 통합 평가 |
| interview | GET | `/interview/tts/voices` | TTS 보이스 목록 |
| interview | POST | `/interview/tts` | 질문 음성 합성 (Google Cloud TTS) |
| resume | POST | `/resume/document/analyze` | (내부) 이력서 텍스트 구조화 |
| resume | POST | `/resume/document/generate` | (내부) 이력서 초안 생성 |
| resume | GET/POST | `/resume/self-introduction/*` | 자기소개서 질문/생성/파싱/첨삭 |
| resume | GET/POST | `/resume/project/*` | 프로젝트(STAR) 질문/생성/첨삭 |
| resume | POST | `/resume/technical-summary/synthesize` | (내부) 기술 요약 생성 |
| assistant | POST | `/assistant/chat` | 사이트 전역 챗봇 |
| timeline | POST | `/timeline/insight/generate` | 성장 인사이트 생성 |
| certificate | POST | `/certificates/study-plan/generate` | 자격증 학습 계획 생성 |
| matching | GET | `/matching/status` | 추천 모델 상태 |
| matching | POST | `/matching/retrain` | (내부) 모델 재학습 |
| matching | POST | `/matching/score-batch` | (내부) 매칭 점수 배치 계산 |
| - | GET | `/health` | 헬스체크 |

### 4.3 Wordcloud / 얼굴인증 서버 (`:8000`)

| Method | Path | 설명 |
|---|---|---|
| GET | `/health` | 헬스체크 |
| GET | `/api/wordcloud?importance=` | 채용요건 키워드 워드클라우드 (캐시/즉시 렌더링) |
| POST | `/api/internal/admin/face/verify` | (내부) DeepFace 관리자 얼굴 인증 |

---

## 5. 외부 연동 서비스

| 서비스 | 호출 주체 | 용도 |
|---|---|---|
| Google Gemini API | Backend, AI Server | 이력서 지원, 모의면접 질문 폴리싱 |
| Google Cloud Speech-to-Text | AI Server | 모의면접 답변 STT |
| Google Cloud TTS | AI Server | 모의면접 질문 음성 합성 |
| Kakao Map JS SDK | 브라우저(클라이언트 직접) | 지도 기반 채용공고 |
| Toss Payments SDK | 브라우저(클라이언트) + Backend | 구독 결제 |
| OAuth2 (Google/Kakao/Naver) | Backend | 소셜 로그인 |
| Web Push (VAPID) | Backend → 브라우저 | 마감임박/추천 알림 |
| Bareun.ai | Backend | 맞춤법 검사 |
| Q-Net, CareerNet, 사람인(SaraminDATA), 알라딘 | Backend | 자격증/학교전공/채용/도서 데이터 연동 |

---

## 6. (예정) 패스워드리스 인증 인프라

현재는 미구현이며, 별도 EC2에 새로 구축 예정입니다.

| 컴포넌트 | 포트 | 역할 |
|---|---|---|
| 등록 서버 | 8080 | QR 등록 · 회원 확인 |
| REST 인증 서버 | 11040 | 인증 요청 · 결과 확인 |
| 푸시 서버 (WebSocket) | 15010 | 모바일 앱에 패스워드리스 인증 푸시 발송 |
| 모바일 인증 앱 | - | 푸시 수신 → 지문/PIN 승인 |

예상 흐름: Backend가 로그인 시 REST 인증 서버에 인증 요청 → 인증 서버가 푸시 서버를 통해 사용자 모바일 앱에 푸시 발송 → 사용자가 지문/PIN으로 승인 → 푸시 서버가 결과를 REST 인증 서버에 회신 → Backend가 폴링/콜백으로 결과 확인 후 JWT 발급. (현재 기존 로그인 체계와의 통합 방식은 미정 — 기존 ID/PW·소셜 로그인과 병행할지, 특정 시나리오(관리자 등)에만 적용할지는 설계 단계에서 확정 필요)
