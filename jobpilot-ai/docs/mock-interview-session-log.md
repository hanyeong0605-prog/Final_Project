# 모의면접 기능 작업 로그 (2026-08-05)

Claude Desktop(Cowork)과의 대화 세션은 이 컴퓨터에만 로컬로 저장되고 계정에 동기화되지
않는다 - 데스크톱이 다시 고장 나거나 앱을 재설치하면 대화 자체는 사라질 수 있다. 코드는
git(GitHub)에 있어서 안전하지만, "왜 이렇게 짰는지" 같은 맥락은 코드만 봐서는 다시
복원하기 어려워서 이 문서에 남겨둔다. 새 세션에서 이어서 작업할 때 이 문서부터 읽으면 됨.

## 1. numba / Windows 스마트 앱 컨트롤 차단 문제

**증상**: `/analyze-answer` 호출 시 `ImportError: DLL load failed while importing _dynfunc:
애플리케이션 제어 정책에서 이 파일을 차단했습니다.`

**원인 (2단계로 추적됨)**:
1. `librosa`가 내부적으로 numba를 쓴다(피치 추출 등) → librosa 의존성 자체를 제거하고
   `audio_analysis.py`를 numpy 전용으로 재작성(정규화 자기상관 피치 추정, RMS 기반 무음
   구간 계산). 그런데 제거 후에도 똑같은 에러가 재현됨.
2. `openai-whisper` 자체가 numba에 의존하고 있었다. `whisper/__init__.py` →
   `transcribe.py` → `timing.py` 순으로 최상단 import가 이어지는데 `timing.py`에
   `import numba`가 있음. `word_timestamps` 옵션을 켜지 않아도(우리는 안 씀) `import
   whisper` 시점에 무조건 실행됨.

**조치**: `audio_analysis.py`의 `_install_numba_stub_if_needed()` - 진짜 numba 로드가
실패하는 환경에서만 `@numba.jit`을 그대로 통과시키는 가짜 numba 모듈을 `sys.modules`에
심어서 whisper의 numba import를 우회한다. numba가 정상 로드되는 환경에서는 진짜 걸
그대로 쓴다.

## 2. STT(whisper) 품질

`base` 모델 + 기본 설정만으로는 한국어 인식이 자주 깨짐(무의미한 영단어 환각 등 - 전형적인
whisper 환각 증상). 적용한 것:

- `_trim_silence()`: STT에 넘기기 전 답변 앞뒤 무음만 트리밍(중간 긴 침묵은 안 건드림 -
  `analyze_voice`의 침묵 지표와는 무관). whisper가 무음 구간에서 환각을 만드는 경향을
  줄이는 목적.
- `initial_prompt`로 "채용 면접 답변"이라는 문맥을 미리 줌.
- `TranscriptionResult(text, low_confidence)` - whisper 세그먼트 `avg_logprob` 평균이
  임계값(-1.0)보다 낮으면 `low_confidence=True`. 공식 신뢰도 점수는 아니고 경험적
  휴리스틱. 프론트에 경고 배지로 표시(`/analyze-answer` 응답의
  `low_confidence_transcript` 필드).

**모델 업그레이드 (완료)**: `base` → `small`로 변경함. 실제 답변 오디오(카카오톡 mp4)로
`ml/debug_transcribe.py`를 이용해 base/small을 직접 비교한 결과, small이 확신도
(avg_logprob)도 높고 "통악"→"통학", "공정계사원"→"공정개선"처럼 오인식을 바로잡았고,
특히 "4%"→"46%"처럼 숫자 자체가 틀리던 것까지 고쳐졌다(성과 수치 오인식은 면접 답변에서
치명적이라 이 차이를 크게 봄). CPU 기준 답변 1건당 추론 시간이 +3초 정도 늘지만(2.1초 →
5.4초), 턴제(답변 끝난 뒤 분석) 구조라 감수할 만하다고 판단.

**아직 안 한 것 / 다음 단계**:
- 테스트용 스크립트: `ai-server/ml/debug_transcribe.py` - 마이크로 직접 말하지 않고
  아무 한국어 오디오/영상 파일로 여러 모델을 한번에 비교 가능. (cmd에서 직접 실행할 땐
  프로젝트 루트를 sys.path에 못 잡는 문제, whisper를 numba 스텁보다 먼저 import하면
  안 되는 문제 둘 다 이미 고쳐서 지금 버전은 바로 실행 가능함)
- 단어 단위 신뢰도 하이라이트(지금은 답변 전체에 배지 하나) - `word_timestamps=True`를
  켜면 가능한데(위 numba 스텁 덕분에 이제 동작은 함, JIT 가속만 없음), 이건 "이미 나온
  텍스트에 표시를 더 촘촘하게" 하는 것뿐이고 인식 품질 자체를 올리진 않는다는 점 주의.

## 3. Gemini 종합 평가 리포트

질문 + STT 답변 + 음성 지표(`audio_analysis.py`) + 얼굴 지표(프론트 MediaPipe)를 모아
Gemini가 종합 평가 문단을 생성한다.

- 백엔드: `evaluation.py`의 `generate_report()`, `router.py`의 `POST /evaluate`.
- 프론트: 답변 분석 직후 자동 호출 안 하고 "종합 평가 보기" 버튼을 눌러야 호출됨(Gemini
  호출 비용 절약 목적).
- 설계 원칙: "긴장도 68%" 같은 확신에 찬 심리 판독 금지. 측정값은 "경향"으로만 언급.
  평균 피치(pitch_mean_hz) 자체를 절대적으로 "높다/낮다" 판단하지 않고, 변동폭(패턴)을
  근거로 서술하게 프롬프트에 명시.

## 4. 프론트 버그 수정

- 녹음 중/분석 중/결과 화면에서 "다른 질문"·"질문 듣기" 버튼이 계속 활성화돼 있어서
  실수로 누르면 진행 중인 답변이 날아가던 문제 → `isAnswerInProgress`로 비활성화.
- React 18 StrictMode가 개발 모드에서 마운트 이펙트를 두 번 실행하는 것 때문에
  `loadNextQuestion()`이 연달아 두 번 불려서, 카메라 준비 중에 질문이 자동으로 바뀌던
  버그 → `hasInitialLoadRef`로 최초 1회만 실행되게 가드.
- 녹음 타이머가 2초씩 뛰던 문제 → `startRecording`에서 기존 `setInterval`을 지우지 않고
  새로 만드는 경우가 있어서, 시작 전에 항상 기존 타이머를 정리하도록 방어 코드 추가.

## 5. 결과 화면 게이지 바

말속도/침묵 비율처럼 "정상 범위" 기준이 있는 지표에 `RangeGauge`
(`shared/components/RangeGauge.tsx`) 컴포넌트로 범위 밴드 + 실제값 마커를 시각화.
기준이 없는 지표(피치 변동폭, 음량 떨림 등)는 게이지를 안 그림 - 없는 기준을 있는 것처럼
보여주지 않기 위함.

## 6. 보류된 작업

- 결제(크레딧) 기능: 설계만 논의됨(Toss Payments 테스트모드, `member_interview_quota` +
  `credit_purchase` 테이블, `interview`/`payment` 도메인). 코드 착수 안 함 - Gemini 리포트
  기능을 먼저 끝내기로 함.
