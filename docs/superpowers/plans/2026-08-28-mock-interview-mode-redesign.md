# Mock Interview Mode Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 무료 코퍼스 기반 모의면접과 구독자용 Gemini/RAG 실전면접을 분리하고, 신뢰도 기반 MediaPipe 비언어 행동 리뷰까지 제공한다.

**Architecture:** 기존 `MockInterviewPage`의 녹음·TTS·STT·세션 진행 흐름은 보존하고 모드 설정과 질문 슬롯 정책을 작은 프론트 모듈로 분리한다. AI 서버는 무료 코퍼스 경로와 유료 RAG 경로를 명시적인 요청 필드로 구분하며, MediaPipe는 브라우저에서 보정·집계한 수치만 서버로 전달한다. 종합 리포트의 비언어 리뷰는 선택 필드로 Spring 세션 기록까지 저장한다.

**Tech Stack:** React 18, TypeScript 5.6, Vite 5, Vitest, MediaPipe Tasks Vision, FastAPI/Pydantic, pytest, Gemini API, Spring Boot/JPA/Flyway

**Spec:** `docs/superpowers/specs/2026-08-28-mock-interview-mode-redesign.md`

## Global Constraints

- 무료 모의면접은 Gemini를 호출하지 않고 코퍼스만 사용한다.
- 무료 질문 수는 자기소개 포함 2~5개, 실전 질문 수는 자기소개·마지막 포부 포함 5~10개다.
- 실전면접은 구독자 전용이며 `스펙만 / 스펙+회사 / 회사` 근거를 지원한다.
- 모의면접과 실전면접 모두 기존 STT, TTS 음성 선택, MediaPipe, 질문 스킵, 종합 리포트를 유지한다.
- 얼굴 영상과 원시 랜드마크는 브라우저 밖으로 전송하거나 저장하지 않는다.
- 감정, 긴장, 자신감, 성격, 진실성을 카메라 데이터로 판정하지 않는다.
- 질문 생성 실패는 중복 없는 코퍼스 질문으로 대체하고, 전체 수를 못 채우면 명시적인 재시도 오류를 표시한다.

---

### Task 1: 프론트 테스트 기반과 질문 슬롯 정책

**Files:**
- Modify: `jobpilot-ai/frontend/package.json`
- Create: `jobpilot-ai/frontend/src/features/mock-interview/model/interviewConfig.ts`
- Create: `jobpilot-ai/frontend/src/features/mock-interview/model/interviewConfig.test.ts`

**Interfaces:**
- Produces: `clampQuestionCount(kind, value): number`
- Produces: `buildPracticeCategories(type, count): InterviewCategory[]`
- Produces: `buildRealInterviewSlots(count): RealInterviewSlot[]`

- [x] **Step 1: Vitest 스크립트와 의존성 추가**

`package.json`에 `"test": "vitest run"`을 추가하고 `devDependencies`에 `"vitest": "^2.1.9"`를 추가한다. React DOM 렌더링 테스트는 이번 작업에서 필요하지 않으므로 Testing Library와 jsdom은 추가하지 않는다.

- [x] **Step 2: 질문 수와 슬롯 배분 실패 테스트 작성**

```ts
import { describe, expect, it } from "vitest";
import { buildRealInterviewSlots, clampQuestionCount } from "./interviewConfig";

describe("interview question policy", () => {
  it("clamps practice to 2..5 and real to 5..10", () => {
    expect(clampQuestionCount("practice", 0)).toBe(2);
    expect(clampQuestionCount("practice", 9)).toBe(5);
    expect(clampQuestionCount("real", 2)).toBe(5);
    expect(clampQuestionCount("real", 12)).toBe(10);
  });

  it.each([[5, 1], [7, 1], [8, 2], [10, 2]])(
    "allocates behavioral slots for %i questions",
    (count, behavioral) => {
      const slots = buildRealInterviewSlots(count);
      expect(slots).toHaveLength(count);
      expect(slots[0].kind).toBe("intro");
      expect(slots.at(-1)?.kind).toBe("closing");
      expect(slots.filter((slot) => slot.kind === "behavioral")).toHaveLength(behavioral);
    },
  );
});
```

- [x] **Step 3: 실패 확인**

Run: `cd jobpilot-ai/frontend && npm install && npm test -- interviewConfig.test.ts`

Expected: FAIL because `interviewConfig.ts` does not exist.

- [x] **Step 4: 순수 정책 모듈 구현**

```ts
export type InterviewKind = "practice" | "real";
export type InterviewType = "종합" | "인성" | "역량" | "직무";
export type InterviewCategory =
  | "가치관_자기관리"
  | "협업_리더십_커뮤니케이션"
  | "문제해결_도전경험"
  | "강점_약점"
  | "기술_직무역량";
export type RealInterviewSlot = {
  kind: "intro" | "rag" | "behavioral" | "closing";
  category?: InterviewCategory;
  angle?: string;
};

export function clampQuestionCount(kind: InterviewKind, value: number): number {
  const [min, max] = kind === "practice" ? [2, 5] : [5, 10];
  return Math.min(max, Math.max(min, Number.isFinite(value) ? Math.round(value) : min));
}
```

`buildPracticeCategories`는 종합일 때 인성→역량→직무를 순환하고, 특정 유형일 때 해당 카테고리만 순환한다. `buildRealInterviewSlots`는 첫/마지막 고정 슬롯과 5~7개일 때 행동 1개, 8~10개일 때 행동 2개를 배치하고 나머지를 서로 다른 `angle`의 RAG 슬롯으로 채운다.

- [x] **Step 5: 테스트와 타입 검사 통과 확인**

Run: `cd jobpilot-ai/frontend && npm test -- interviewConfig.test.ts && npm run build`

Expected: PASS and Vite production build succeeds.

- [x] **Step 6: 커밋**

```bash
git add jobpilot-ai/frontend/package.json jobpilot-ai/frontend/package-lock.json jobpilot-ai/frontend/src/features/mock-interview/model/interviewConfig.ts jobpilot-ai/frontend/src/features/mock-interview/model/interviewConfig.test.ts
git commit -m "test: add interview question policy"
```

### Task 2: 회원 스펙 RAG 조회 모듈

**Files:**
- Create: `jobpilot-ai/ai-server/app/domain/interview/member_spec_retrieval.py`
- Create: `jobpilot-ai/ai-server/tests/test_member_spec_retrieval.py`
- Modify: `jobpilot-ai/ai-server/app/domain/interview/job_requirement_retrieval.py`

**Interfaces:**
- Produces: `fetch_member_spec(member_id: int | None) -> MemberSpec | None`
- Produces: `build_member_spec_context(member_id, category, spec=None) -> str | None`
- Produces: `build_gap_context(spec, requirements) -> str | None`

- [x] **Step 1: 스펙 없음·스펙 있음·공고 대조 실패 테스트 작성**

```py
def test_none_member_skips_database():
    assert fetch_member_spec(None) is None

def test_gap_context_marks_only_verified_matches():
    spec = MemberSpec(target_role="백엔드", skills=[MemberSkillRow("Java", "기술")], projects=[], certificates=[])
    requirements = [
        JobRequirementRow("SKILL", "Java 경험", "REQUIRED", "Java 경험 필수", "VERIFIED"),
        JobRequirementRow("SKILL", "Kubernetes 운영", "PREFERRED", "Kubernetes 우대", "VERIFIED"),
    ]
    context = build_gap_context(spec, requirements)
    assert "보유 근거 확인: Java 경험" in context
    assert "스펙에서 확인되지 않음: Kubernetes 운영" in context
```

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_member_spec_retrieval.py -v`

Expected: FAIL because module and dataclasses do not exist.

- [x] **Step 3: 읽기 전용 스펙 조회 구현**

회원 기본 프로필, 보유 기술, 프로젝트, 자격증과 저장된 자기소개서 요약을 기존 DB 연결 헬퍼로 조회한다. 반환 데이터는 질문 프롬프트에 필요한 짧은 문자열만 포함하고 로그와 API 응답에는 원문을 출력하지 않는다. `member_id is None`이면 DB 연결을 만들지 않고 즉시 `None`을 반환한다.

- [x] **Step 4: 스펙·공고 대조 구현**

요구사항과 보유 기술은 정규화된 키워드 일치로만 `확인` 처리한다. 일치하지 않는 항목은 결핍으로 단정하지 않고 `스펙에서 확인되지 않음`으로 표현한다.

- [x] **Step 5: 테스트 통과 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_member_spec_retrieval.py tests/test_question_corpus.py -v`

Expected: PASS.

- [x] **Step 6: 커밋**

```bash
git add jobpilot-ai/ai-server/app/domain/interview/member_spec_retrieval.py jobpilot-ai/ai-server/app/domain/interview/job_requirement_retrieval.py jobpilot-ai/ai-server/tests/test_member_spec_retrieval.py
git commit -m "feat: add member specification retrieval for interviews"
```

### Task 3: 무료·실전 질문 생성 API 계약

**Files:**
- Modify: `jobpilot-ai/ai-server/app/domain/interview/router.py`
- Modify: `jobpilot-ai/ai-server/app/domain/interview/question_generator.py`
- Modify: `jobpilot-ai/ai-server/tests/test_router_next_question.py`
- Modify: `jobpilot-ai/ai-server/tests/test_question_generator.py`
- Modify: `jobpilot-ai/frontend/src/features/mock-interview/api/mockInterviewApi.ts`

**Interfaces:**
- Extends request: `mode: "practice" | "real"`, `source: "spec" | "spec_company" | "company" | None`, `member_id`, `job_posting_id`, `angle_hint`, `exclude`
- Preserves response: `{ "question": string }`

- [x] **Step 1: 무료 요청이 Gemini를 호출하지 않는 테스트 작성**

```py
def test_practice_mode_uses_corpus_only(monkeypatch):
    monkeypatch.setattr(router.question_corpus, "pick_question", lambda *args: "코퍼스 질문")
    monkeypatch.setattr(router, "generate_personalized_question", lambda **kwargs: (_ for _ in ()).throw(AssertionError()))
    response = router.next_question(NextQuestionRequest(mode="practice", category="기술_직무역량"))
    assert response == {"question": "코퍼스 질문"}
```

- [x] **Step 2: 실전 세 가지 근거 조합 테스트 작성**

`source=spec`은 회원 스펙만, `source=company`는 공고만, `source=spec_company`는 두 문맥과 gap 문맥을 생성 함수에 전달하는지 mock으로 캡처해 검증한다. 실전 요청에서 필요한 ID가 빠졌으면 HTTP 400을 기대한다.

- [x] **Step 3: 실패 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_router_next_question.py -v`

Expected: FAIL because `mode` and `source` are not supported.

- [x] **Step 4: 서버 계약과 검증 구현**

```py
class NextQuestionRequest(BaseModel):
    mode: Literal["practice", "real"] = "practice"
    source: Literal["spec", "spec_company", "company"] | None = None
    job: str = DEFAULT_JOB
    category: str = ""
    angle_hint: str = ""
    exclude: list[str] = []
    job_posting_id: int | None = None
    member_id: int | None = None
```

`mode=practice`는 항상 코퍼스로 즉시 반환한다. `mode=real`은 source별 필수 ID를 검증한 뒤 필요한 RAG 문맥만 조회한다.

- [x] **Step 5: 전문 면접관 질문 프롬프트 구현**

`generate_personalized_question`에 `interview_mode`, 세 RAG 문맥과 `angle_hint`를 전달한다. 프롬프트는 질문 한 문장, 사실 근거, TTS 친화적 구어체, 중복 금지, 심리 압박 금지를 명시한다. 행동 슬롯도 Gemini를 사용하되 제공되지 않은 경험을 전제하지 않는다.

- [x] **Step 6: 프론트 API 타입 동기화**

```ts
export type InterviewQuestionSource = "spec" | "spec_company" | "company";
export async function fetchNextQuestion(options: {
  mode: "practice" | "real";
  source?: InterviewQuestionSource;
  job?: string;
  category?: string;
  angleHint?: string;
  exclude?: string[];
  jobPostingId?: number;
  memberId?: number;
}): Promise<NextQuestionResponse>
```

- [x] **Step 7: 관련 테스트 통과 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_router_next_question.py tests/test_question_generator.py -v`

Expected: PASS.

- [x] **Step 8: 커밋**

```bash
git add jobpilot-ai/ai-server/app/domain/interview/router.py jobpilot-ai/ai-server/app/domain/interview/question_generator.py jobpilot-ai/ai-server/tests/test_router_next_question.py jobpilot-ai/ai-server/tests/test_question_generator.py jobpilot-ai/frontend/src/features/mock-interview/api/mockInterviewApi.ts
git commit -m "feat: split practice and real interview question generation"
```

### Task 4: 모드 설정 UI와 질문 수 컨트롤

**Files:**
- Create: `jobpilot-ai/frontend/src/features/mock-interview/components/QuestionCountControl.tsx`
- Create: `jobpilot-ai/frontend/src/features/mock-interview/components/InterviewSetupPanel.tsx`
- Modify: `jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx`
- Modify: `jobpilot-ai/frontend/src/styles.css`

**Interfaces:**
- Consumes: `clampQuestionCount`, `InterviewKind`, `InterviewType`
- Produces: `InterviewSetupValue` containing kind, target role, type, count, real source and selected posting

- [x] **Step 1: 숫자 컨트롤의 순수 이벤트 규칙 테스트 추가**

`interviewConfig.test.ts`에 빈 문자열은 blur 시 최솟값, `+/-`는 범위 보정, 소수는 반올림하는 사례를 추가한다.

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/frontend && npm test -- interviewConfig.test.ts`

Expected: FAIL for the new normalization cases.

- [x] **Step 3: 재사용 숫자 컨트롤 구현**

`QuestionCountControl`은 실제 `input type="number"`, `min`, `max`, `aria-describedby`를 제공한다. 버튼은 입력 양옆에 `−`와 `+`로 배치하고 최대값 안내를 가까이 표시한다.

- [x] **Step 4: 세그먼트형 라디오와 단계별 설정 구현**

`InterviewSetupPanel`은 `fieldset/legend`와 실제 radio input을 사용한다. 무료 모드는 목표 직무·질문 유형·2~5개를, 실전 모드는 source·공고·5~10개를 렌더링한다. 저장된 목표 직무는 기본값이지만 변경 가능하게 한다.

- [x] **Step 5: 구독 및 입력 이동 구현**

비구독자가 실전을 누르면 설정값을 변경하지 않고 모달을 연다. `구독하러 가기`는 `/account`, 스펙 부족의 `스펙 입력하기`는 `/capability?tool=profile`로 이동한다. 회사가 필요한 source는 공고가 선택되기 전 시작 버튼을 비활성화한다.

- [x] **Step 6: CSS와 빌드 확인**

Run: `cd jobpilot-ai/frontend && npm test && npm run build`

Expected: PASS; 360px 너비에서도 세그먼트와 숫자 컨트롤이 넘치지 않는다.

- [x] **Step 7: 커밋**

```bash
git add jobpilot-ai/frontend/src/features/mock-interview/components/QuestionCountControl.tsx jobpilot-ai/frontend/src/features/mock-interview/components/InterviewSetupPanel.tsx jobpilot-ai/frontend/src/features/mock-interview/model/interviewConfig.test.ts jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx jobpilot-ai/frontend/src/styles.css
git commit -m "feat: redesign interview mode setup"
```

### Task 5: 모드별 세션 질문 조립

**Files:**
- Create: `jobpilot-ai/frontend/src/features/mock-interview/lib/buildInterviewQuestions.ts`
- Create: `jobpilot-ai/frontend/src/features/mock-interview/lib/buildInterviewQuestions.test.ts`
- Modify: `jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx`

**Interfaces:**
- Consumes: `buildPracticeCategories`, `buildRealInterviewSlots`, `fetchNextQuestion`
- Produces: `buildInterviewQuestions(config, dependencies): Promise<string[]>`

- [x] **Step 1: 무료·실전 질문 순서와 폴백 테스트 작성**

mock된 `fetchQuestion`으로 무료 첫 질문, 실전 첫/마지막 질문, 회사명 포함 포부, 행동 슬롯 수, 중복 응답의 코퍼스 재호출을 검증한다.

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/frontend && npm test -- buildInterviewQuestions.test.ts`

Expected: FAIL because builder does not exist.

- [x] **Step 3: 질문 빌더 구현**

무료는 자기소개 다음에 코퍼스 요청을 순차 실행해 누적 exclude를 전달한다. 실전은 슬롯별 Gemini 요청을 병렬 실행한 뒤 입력 순서대로 조립하고, 실패·중복 슬롯만 코퍼스 요청으로 대체한다. 대체 후에도 목표 길이를 못 채우면 `InterviewQuestionBuildError`를 던진다.

- [x] **Step 4: 페이지 연결 및 스킵 회귀 확인**

기존 `buildSessionQuestions` 본문을 새 빌더 호출로 교체한다. `skipCurrentQuestion`은 음성·텍스트 모드 모두에서 분석 API를 호출하지 않고 명시적인 스킵 문구를 저장한 뒤 다음 슬롯으로 진행해야 한다.

- [x] **Step 5: 테스트와 빌드 통과 확인**

Run: `cd jobpilot-ai/frontend && npm test && npm run build`

Expected: PASS.

- [x] **Step 6: 커밋**

```bash
git add jobpilot-ai/frontend/src/features/mock-interview/lib/buildInterviewQuestions.ts jobpilot-ai/frontend/src/features/mock-interview/lib/buildInterviewQuestions.test.ts jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx
git commit -m "feat: build mode-specific interview sessions"
```

### Task 6: MediaPipe 보정과 신뢰도 기반 집계

**Files:**
- Modify: `jobpilot-ai/frontend/src/features/mock-interview/lib/faceAnalysis.ts`
- Create: `jobpilot-ai/frontend/src/features/mock-interview/lib/faceAnalysis.test.ts`
- Modify: `jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx`

**Interfaces:**
- Produces: `FaceCalibration`, `FaceFrameSample`, `FaceMetrics`
- Produces: `buildCalibration(samples): FaceCalibration | null`
- Produces: `summarizeFaceFrames(frames, durationSec, calibration): FaceMetrics | null`

- [x] **Step 1: 보정·회전·중앙 유지·신뢰도 테스트 작성**

```ts
it("marks sparse short recordings as insufficient", () => {
  const metrics = summarizeFaceFrames(makeFrames(8), 2, calibration);
  expect(metrics?.confidence).toBe("insufficient");
});

it("measures head rotation relative to calibration", () => {
  const metrics = summarizeFaceFrames(makeRotatedFrames({ yaw: 12 }), 30, calibration);
  expect(metrics?.headOffCenterRatio).toBeGreaterThan(0);
});
```

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/frontend && npm test -- faceAnalysis.test.ts`

Expected: FAIL because calibration and confidence fields do not exist.

- [x] **Step 3: MediaPipe 출력 확장**

`outputFacialTransformationMatrixes: true`로 변경한다. 프레임 샘플에는 변환 행렬에서 추출한 yaw/pitch, 얼굴 윤곽 중심·크기, 홍채 상대 위치와 유효 여부만 저장한다. 원시 478개 랜드마크 배열은 샘플에 보존하지 않는다.

- [x] **Step 4: 기기 점검 보정 구현**

기기 점검의 연속 유효 프레임 2~3초에서 yaw/pitch/홍채/얼굴 중심의 중앙값을 `FaceCalibration`으로 만든다. 보정이 완료되지 않으면 면접은 진행할 수 있지만 결과 신뢰도를 `insufficient`로 처리한다.

- [x] **Step 5: 집계와 신뢰도 구현**

`FaceMetrics`에 `headOffCenterRatio`, `cameraGazeRatio`, `faceCenteredRatio`, `validFrameRatio`, `confidence`를 추가한다. 5초 미만 또는 유효 프레임 30개 미만은 `insufficient`, 유효 프레임 비율 60% 미만은 `reference`, 그 외는 `sufficient`로 분류한다. 임계값은 상수로 이름을 붙여 테스트에서 고정한다.

- [x] **Step 6: 페이지 표시와 빌드 확인**

기존 `headMovement`와 `gazeOffCenterRatio` 카드는 새 지표 이름과 설명으로 교체하고 `confidence` 배지를 표시한다. `insufficient`일 때 수치 기반 조언 대신 촬영 환경 안내를 보여준다.

Run: `cd jobpilot-ai/frontend && npm test -- faceAnalysis.test.ts && npm run build`

Expected: PASS.

- [x] **Step 7: 커밋**

```bash
git add jobpilot-ai/frontend/src/features/mock-interview/lib/faceAnalysis.ts jobpilot-ai/frontend/src/features/mock-interview/lib/faceAnalysis.test.ts jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx
git commit -m "feat: improve MediaPipe nonverbal metrics"
```

### Task 7: 비언어적 행동 종합 리뷰

**Files:**
- Modify: `jobpilot-ai/ai-server/app/domain/interview/evaluation.py`
- Modify: `jobpilot-ai/ai-server/tests/test_evaluation.py`
- Modify: `jobpilot-ai/frontend/src/features/mock-interview/model/mockInterview.types.ts`
- Modify: `jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx`

**Interfaces:**
- Extends response: `nonverbal_feedback: str | None`

- [x] **Step 1: 신뢰도별 리포트 테스트 작성**

충분한 얼굴 지표가 있으면 JSON 스키마에 `nonverbal_feedback`을 요구하고, 모든 얼굴 지표가 없거나 `confidence=insufficient`이면 반드시 null을 요구하는 프롬프트 테스트를 작성한다. 파싱 결과가 문자열 또는 null로 보존되는지도 검증한다.

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_evaluation.py -v`

Expected: FAIL because report has no `nonverbal_feedback` field.

- [x] **Step 3: 평가 모델과 프롬프트 구현**

```py
@dataclass
class SessionEvaluationReport:
    ok: bool
    nonverbal_feedback: str | None = None
    # existing fields stay unchanged
```

프롬프트는 충분한 지표가 있을 때만 한 문단의 관찰·연습 조언을 요청한다. 금지어 규칙에는 긴장, 자신감, 성격, 감정, 거짓말 판정을 명시한다. 스킵 문구가 있는 답변은 내용 피드백에서 미응답으로 처리한다.

- [x] **Step 4: 프론트 타입과 별도 섹션 구현**

`SessionEvaluationReport.nonverbal_feedback: string | null`을 추가한다. AI 종합 평가 패널에 `비언어적 행동 리뷰` 제목으로 한 문단을 표시하고 null이면 분석 데이터 부족 안내를 표시한다.

- [x] **Step 5: 테스트와 빌드 확인**

Run: `cd jobpilot-ai/ai-server && pytest tests/test_evaluation.py -v`

Run: `cd jobpilot-ai/frontend && npm run build`

Expected: both PASS.

- [x] **Step 6: 커밋**

```bash
git add jobpilot-ai/ai-server/app/domain/interview/evaluation.py jobpilot-ai/ai-server/tests/test_evaluation.py jobpilot-ai/frontend/src/features/mock-interview/model/mockInterview.types.ts jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx
git commit -m "feat: add nonverbal interview feedback"
```

### Task 8: Spring 세션 기록에 비언어 리뷰 저장

**Files:**
- Create: `jobpilot-ai/backend/src/main/resources/db/migration/V14__interview_nonverbal_feedback.sql`
- Modify: `jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/member/entity/InterviewSessionRecord.java`
- Modify: `jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/member/dto/InterviewSessionRecordRequest.java`
- Modify: `jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/member/dto/InterviewSessionRecordDetailResponse.java`
- Modify: `jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/member/service/InterviewSessionRecordService.java`
- Modify: `jobpilot-ai/backend/src/test/java/com/jobpilot/api/domain/member/service/InterviewSessionRecordServiceTest.java`
- Modify: `jobpilot-ai/frontend/src/features/timeline/model/timeline.types.ts`
- Modify: `jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx`

**Interfaces:**
- Extends session record: `nonverbalFeedback: string | null`

- [x] **Step 1: 저장·조회 서비스 실패 테스트 작성**

`InterviewSessionRecordRequest`에 비언어 리뷰를 넣어 create한 뒤 detail 응답에서 같은 문자열을 돌려받는 테스트를 추가한다. null인 과거 기록도 정상 조회되는 사례를 추가한다.

- [x] **Step 2: 실패 확인**

Run: `cd jobpilot-ai/backend && mvn -Dtest=InterviewSessionRecordServiceTest test`

Expected: compilation FAIL because DTO and entity have no field.

- [x] **Step 3: nullable 마이그레이션과 엔티티 구현**

```sql
ALTER TABLE interview_session_records
    ADD COLUMN nonverbal_feedback TEXT NULL;
```

엔티티 생성자, getter, 요청·상세 응답 DTO와 서비스 매핑에 nullable 필드를 추가한다. 목록 응답은 변경하지 않는다.

- [x] **Step 4: 프론트 저장 요청 연결**

타임라인 입력 타입에 `nonverbalFeedback?: string | null`을 추가하고 `saveInterviewSessionRecord` 호출에 `res.report.nonverbal_feedback`을 전달한다.

- [x] **Step 5: 백엔드 테스트와 프론트 빌드 확인**

Run: `cd jobpilot-ai/backend && mvn -Dtest=InterviewSessionRecordServiceTest test`

Run: `cd jobpilot-ai/frontend && npm run build`

Expected: PASS.

- [x] **Step 6: 커밋**

```bash
git add jobpilot-ai/backend/src/main/resources/db/migration/V14__interview_nonverbal_feedback.sql jobpilot-ai/backend/src/main/java/com/jobpilot/api/domain/member jobpilot-ai/backend/src/test/java/com/jobpilot/api/domain/member/service/InterviewSessionRecordServiceTest.java jobpilot-ai/frontend/src/features/timeline/model/timeline.types.ts jobpilot-ai/frontend/src/pages/MockInterviewPage.tsx
git commit -m "feat: persist nonverbal interview feedback"
```

### Task 9: 전체 회귀 검증과 문서 동기화

**Files:**
- Modify: `jobpilot-ai/docs/mock-interview-tech-reference.md`
- Modify: `jobpilot-ai/docs/mock-interview-portfolio.md`

**Interfaces:**
- Consumes all earlier task outputs.
- Produces a verified end-to-end feature with no uncommitted fixes.

- [x] **Step 1: AI 서버 전체 테스트 실행**

Run: `cd jobpilot-ai/ai-server && pytest -v`

Expected: all tests PASS.

- [x] **Step 2: 백엔드 전체 테스트 실행**

Run: `cd jobpilot-ai/backend && mvn test`

Expected: BUILD SUCCESSFUL.

- [x] **Step 3: 프론트 전체 테스트와 빌드 실행**

Run: `cd jobpilot-ai/frontend && npm test && npm run build`

Expected: all Vitest tests PASS and Vite production build succeeds.

- [x] **Step 4: 수동 흐름 점검**

다음 조합을 브라우저에서 확인한다: 무료 목표 직무 있음/없음, 무료 2개/5개, 비구독 실전 모달, 구독 실전 스펙만/회사/둘 다, 5개/10개, 카메라 있음/없음, 음성/채팅, 질문 스킵, Gemini·TTS 실패 폴백. 네트워크 탭에서 무료 질문 요청 중 Gemini 경로가 호출되지 않는 것도 확인한다.

- [x] **Step 5: 기술 문서 갱신**

실제 구현과 기존 문서가 다른 부분만 수정한다. 무료·실전 질문 정책, 새 요청 필드, MediaPipe 보정·신뢰도, `nonverbal_feedback` 응답과 저장 필드를 기록한다.

- [x] **Step 6: 최종 diff 검사와 커밋**

Run: `git diff --check && git status --short`

Expected: whitespace errors 없음; 문서 변경만 남거나 작업 트리가 깨끗함.

```bash
git add jobpilot-ai/docs
git commit -m "docs: update mock interview technical reference"
```

---

## 검증 메모 (2026-08-29 완료 시점)

모든 Task를 구현하고 커밋했다(`844520a`~`5efe380`). 실행한 검증과, **하지 못한 검증**은 아래와 같다.

**통과한 것**
- ai-server: `pytest tests/` → 161 passed
- 백엔드: `mvn test` → 46 tests, BUILD SUCCESS (V41 마이그레이션 적용 후 Hibernate validate 통과)
- 프론트: `npm test` → 42 passed, `npm run build` 성공
- 로컬 3단(MySQL + Spring 9000 + ai-server 8001 + Vite 5173)을 실제로 띄워 브라우저로 확인:
  모드 세그먼트/구독 모달/스펙·공고 안내/질문 수 입력 보정/실전 source 분기/360px 폭,
  무료 채팅 세션 시작 → 질문 조립 → 전부 건너뛰기 → 결과 화면 도달,
  `/interview/next-question`의 practice/real/400 응답.

**하지 못한 것**
- 카메라·마이크 실제 녹음 경로(하드웨어 없음) → MediaPipe 보정·신뢰도 분류는 단위 테스트로만 검증했다.
  실제 카메라로 기준 자세 보정이 잡히는지, 신뢰도 배지가 어떤 값으로 뜨는지는 확인이 필요하다.
- Gemini 실제 호출(키 없음) → 실전 질문 생성은 코퍼스 폴백 경로로만 확인했다.
  프롬프트 내용 자체는 단위 테스트로 검증했다.
- TTS 실제 재생, 타임라인 저장 후 상세 조회 화면.

**기존에 깨져 있던 것(이번 개편과 무관)**
- `ml/test_field_questions.py`: 주석 처리된 LoRA `generate_question`을 import해서 수집 단계에서 실패.
- `test_load_audio_mono16k_decodes_real_wav_via_ffmpeg`: PATH에 ffmpeg가 있어야 통과.
