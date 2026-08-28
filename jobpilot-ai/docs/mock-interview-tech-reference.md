# 모의면접 · 실전면접 기술 레퍼런스

2026-08-29 모드 개편 기준. 설계 문서는 `docs/superpowers/specs/2026-08-28-mock-interview-mode-redesign.md`,
구현 계획은 같은 이름의 `plans/` 문서를 참고한다. 이 문서는 "지금 코드가 실제로 어떻게 동작하는가"만 적는다.

## 1. 두 가지 모드

| | 무료 모의면접 (`practice`) | 구독자 실전면접 (`real`) |
|---|---|---|
| 질문 출처 | 로컬 코퍼스(AI Hub 기반)만 | Gemini + RAG, 실패 시 코퍼스 폴백 |
| Gemini 호출 | **없음** | 있음 |
| 질문 수 | 2~5개 (자기소개 포함) | 5~10개 (자기소개 + 입사 후 포부 포함) |
| 쓰는 회원 정보 | 목표 직무만 | 근거(source)에 따라 스펙/공고 |
| 첫 질문 | 자기소개 고정 | 자기소개 고정 |
| 마지막 질문 | 없음(일반 질문) | 입사 후 포부 고정 (회사를 골랐으면 회사명 포함) |

무료가 Gemini를 호출하지 않는다는 보장은 **서버에 있다**. `router.next_question`은 함수 맨 앞에서
`mode == "practice"`를 검사하고 코퍼스 질문을 즉시 반환하므로, 프론트가 `member_id`나 `job_posting_id`를
같이 보내더라도 RAG 조회나 Gemini 호출 코드에 도달하지 못한다.

## 2. `POST /interview/next-question` 요청 필드

```jsonc
{
  "mode": "practice" | "real",          // 없으면 practice로 간주한다(비용이 드는 쪽을 기본값으로 두지 않는다)
  "source": "spec" | "spec_company" | "company" | null,  // 실전 전용, 질문 근거
  "member_id": 1,                        // source가 spec/spec_company면 필수
  "job_posting_id": 33,                  // source가 company/spec_company면 필수
  "job": "백엔드",
  "category": "기술_직무역량",
  "angle_hint": "기술 선택 이유와 대안 비교",
  "exclude": ["이미 나온 질문", "..."],
  "tech_summary": "",
  "corpus_only": false                   // mode 도입 전 클라이언트용 별칭(true면 practice로 취급)
}
```

응답은 이전과 같은 `{"question": "..."}`이다.

- `source`가 요구하는 ID가 없으면 **400**을 돌려준다. RAG 조회는 전부 fail-open이라 이걸 막지 않으면
  "실전면접인데 스펙이 하나도 반영되지 않은 질문"이 원인 표시 없이 나온다.
- `source`를 안 보내면(=null) RAG 없이 기존 Gemini 프롬프트로만 생성한다. mode 도입 전 흐름과 동일하다.

### 근거(source)별 RAG 문맥

| source | 조회하는 것 | 프롬프트에 들어가는 블록 |
|---|---|---|
| `spec` | `member_spec_retrieval.fetch_member_spec` | 회원이 저장한 스펙 |
| `company` | `job_requirement_retrieval.build_job_requirements_context` | 공고 요구사항 |
| `spec_company` | 둘 다 + `build_gap_context` | 스펙, 공고 요구사항, 그리고 둘의 대조 |

`spec_company`의 대조 블록은 요구사항마다 `보유 근거 확인` / `스펙에서 확인되지 않음`으로만 표기한다.
후자를 "없다"로 단정하지 않는 이유는 스펙에 입력하지 않았을 뿐 실제로는 보유했을 수 있기 때문이고,
프롬프트에도 단정·추궁 금지 규칙을 함께 건다.

### 개인정보 취급

`member_spec_retrieval`은 프롬프트에 필요한 짧은 문자열만 담아 반환한다. 자기소개서는 원문을 싣지 않고
400자까지만 잘라 쓰며(조회 시점과 프롬프트 조립 시점 양쪽에서 자른다), 조회 결과를 로그로 남기지 않는다.
이 블록은 Gemini 프롬프트 입력용이며 API 응답으로 나가지 않는다.

## 3. 프론트 질문 조립

`features/mock-interview/lib/buildInterviewQuestions.ts`가 담당한다(화면 상태와 분리된 순수 함수).

- **무료**: 자기소개 다음부터 카테고리를 순환하며 **순차** 호출. 매 호출에 지금까지 확정된 질문을
  `exclude`로 넘기므로 코퍼스 안에서 중복이 원천 차단된다.
- **실전**: `buildRealInterviewSlots(count)`가 만든 슬롯(자기소개 → RAG 질문들 → 행동 질문 → 포부)을
  **병렬** 호출한 뒤 슬롯 순서대로 조립한다. 병렬이라 서로의 결과를 모르므로 중복이 날 수 있고,
  실패하거나 중복된 슬롯만 코퍼스 질문으로 대체한다.
- 행동 질문은 5~7개면 1개, 8~10개면 2개 배정한다.
- 대체 후에도 목표 개수를 못 채우면 `InterviewQuestionBuildError`를 던진다. 중복 질문으로 자리를
  메우지 않고 사용자에게 다시 시도할 화면을 보여준다.

## 4. MediaPipe 비언어 지표

`features/mock-interview/lib/faceAnalysis.ts`.

### 기준 자세 보정(FaceCalibration)

기기 점검 단계에서 **연속으로 얼굴이 잡힌 구간**의 yaw/pitch/홍채 위치/얼굴 중심 중앙값을 잡는다
(최소 20프레임, 2초 이상). 카메라 높이와 평소 자세는 사람마다 다르므로, 보정 없이 절대 각도로 재면
노트북을 옆에 두고 쓰는 사람은 가만히 있어도 계속 "고개를 돌리고 있다"로 집계된다.
보정에 실패하면 면접은 진행하되 결과 신뢰도를 `insufficient`로 처리한다.

### 지표

| 필드 | 의미 |
|---|---|
| `headOffCenterRatio` | 기준 자세 대비 고개가 크게 돌아가 있던 유효 프레임 비율 |
| `cameraGazeRatio` | 홍채 위치 **와** 고개 방향이 모두 정면에 가까웠던 비율 |
| `faceCenteredRatio` | 얼굴 중심·크기가 권장 영역 안이던 비율 |
| `validFrameRatio` | 전체 프레임 중 얼굴이 인식된 비율 |
| `confidence` | `sufficient` / `reference` / `insufficient` |
| `headMovement` | 코끝 2D 이동량 기반 **보조** 지표(절대 각도가 아님) |

임계값은 전부 이름 붙은 상수로 export하며(`HEAD_YAW_TOLERANCE_DEG` 등) 테스트가 그 값을 그대로 쓴다.

### 신뢰도 판정

- 답변 5초 미만, 유효 프레임 30개 미만, **또는 보정 실패** → `insufficient`
- 유효 프레임 비율 60% 미만 → `reference`
- 그 외 → `sufficient`

`insufficient`면 화면에 수치를 아예 표시하지 않고 촬영 환경 안내만 보여준다. 근거로 쓸 수 없는 숫자를
띄워두면 사용자는 그걸 근거로 받아들인다.

얼굴이 인식되지 않은 프레임도 샘플로 남긴다(`valid: false`). 버리면 "카메라에 얼굴이 거의 안 잡혔다"는
사실 자체가 지표에서 사라진다.

## 5. 비언어 행동 리뷰 (`nonverbal_feedback`)

- `POST /interview/evaluate-session` 응답에 `nonverbal_feedback: string | null`이 추가됐다.
- 신뢰도가 `sufficient`인 얼굴 지표가 하나라도 있을 때만 생성을 요청하고, 없으면 프롬프트가
  **반드시 null로 출력하라**고 지시한다.
- 프롬프트에 넘기는 얼굴 지표는 위 보정 기반 비율과 신뢰도이며, 예전 `headMovement`는 빼뒀다
  (무엇과 비교한 수치인지 설명할 수 없어서다).
- 긴장·자신감·감정·성격·진실성 추정은 금지어 규칙으로 막는다.
- 건너뛴 답변은 `(사용자가 이 질문을 건너뛰었습니다)`로 저장되며, 프롬프트가 이를 미응답으로 다루도록
  명시한다(문구는 `evaluation.SKIPPED_ANSWER_TRANSCRIPT` 상수).

### 저장

`V41__interview_nonverbal_feedback.sql`이 `interview_session_records.nonverbal_feedback TEXT NULL`을
추가한다. 과거 기록과 카메라 미사용 세션은 null이며, 목록 응답(summary)은 바뀌지 않았다.

> 계획서에는 V14로 적혀 있으나 그 번호는 이미 사용 중이라 다음 빈 번호인 V41로 만들었다.

## 6. 테스트

| 대상 | 명령 |
|---|---|
| ai-server | `cd jobpilot-ai/ai-server && pytest tests/` |
| 백엔드 | `cd jobpilot-ai/backend && mvn test` |
| 프론트 | `cd jobpilot-ai/frontend && npm test && npm run build` |

`ml/test_field_questions.py`는 주석 처리된 LoRA 경로(`generate_question`)를 import해서 수집 단계에서
실패한다 - 개편 이전부터 깨져 있던 실험용 스크립트라 `tests/`만 돌리면 된다.
`test_load_audio_mono16k_decodes_real_wav_via_ffmpeg`는 PATH에 ffmpeg가 있어야 통과한다.
