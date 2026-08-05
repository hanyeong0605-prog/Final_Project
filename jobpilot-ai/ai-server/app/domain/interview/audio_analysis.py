"""모의면접 답변 오디오 분석.

2026-08-03 설계 메모: "표정/음성으로 감정·긴장도를 판독한다"는 컨셉은 하지 않는다.
감정 인식은 연구 단계에서도 정확도가 낮고, 확신에 찬 "긴장도 68%" 같은 숫자를 내면
오히려 신뢰도만 떨어뜨린다("애매하게 할 거면 안 하는 게 낫다"는 판단). 대신 근거를
바로 설명할 수 있는 "객관적으로 측정 가능한 지표"만 뽑아서 보여주는 방향으로 잡았다:
  - 말속도 (분당 글자 수)
  - 피치(음높이) 평균/변동폭 - 목소리 톤/떨림의 대리 지표
  - 침묵/멈춤 비율과 긴 침묵 횟수
  - 음량(에너지) 변동폭 - 목소리 떨림의 또 다른 대리 지표
표정/자세는 프론트에서 MediaPipe(Face/Pose Landmark)로 브라우저에서 실시간 측정한다
(실시간 영상은 서버로 스트리밍하기엔 무겁고, 브라우저 클라이언트 처리가 더 적합하다고 판단).

2026-08-05 재작성: 원래 librosa(yin/effects.split/feature.rms)를 썼는데, librosa가 내부적으로
numba를 끌어들이고(피치 추출 등에서 @numba.jit 데코레이터가 모듈 로드 시점에 바로 실행됨),
numba의 네이티브 DLL(_dynfunc.cp3xx-win_amd64.pyd)이 서명 미확인 상태라 Windows 스마트 앱
컨트롤이 로드 자체를 차단하는 환경이 실제로 있었다(팀원 PC에서 확인). "이 컴퓨터에서만
설정을 바꾸면 되는" 문제가 아니라 - 다른 팀원 PC, 나중에 배포할 서버 어디서든 같은 보안
정책이면 똑같이 막힌다 - librosa 의존성 자체를 없애고 numpy만으로 다시 짰다. 오디오
디코딩(webm/mp4 등 -> PCM)은 이미 STT용으로 쓰고 있는 openai-whisper의 ffmpeg 기반
load_audio()를 재사용한다 - 그래서 librosa/soundfile/audioread를 아예 안 거친다.

정확도는 librosa.yin(YIN 알고리즘)보다 단순한 정규화 자기상관 방식이라 약간 떨어지지만,
이 기능의 목적 자체가 "정밀한 피치 트래커"가 아니라 "말투의 경향을 보여주는 참고 지표"라서
이 정도 단순화는 목적에 맞다고 판단했다.

2026-08-05 추가 발견: librosa를 뺀 뒤에도 팀원 PC에서 완전히 똑같은 numba DLL 차단 에러가
재현됐다. GitHub에서 openai-whisper 소스를 직접 확인해보니 원인은 whisper 자체에 있었다 -
whisper/__init__.py가 최상단에서 `from .transcribe import transcribe`를 하고,
transcribe.py는 다시 최상단에서 `from .timing import add_word_timestamps`를 하는데,
timing.py 맨 위에 `import numba`가 있다. 즉 word_timestamps 옵션을 켜지 않아도(우리
transcribe()는 켠 적 없음) "import whisper" 하는 순간 whisper 패키지 초기화 과정에서
무조건 numba가 로드된다 - librosa와는 완전히 독립적인, whisper 자체의 의존성이었던 것.
timing.py가 실제로 쓰는 numba API는 @numba.jit로 감싼 backtrace()/dtw_cpu() 두 함수뿐이고
(둘 다 word_timestamps=True일 때만 실제로 호출됨), 우리는 그 옵션을 안 쓰므로 이 두 함수가
호출될 일이 없다. 그래서 whisper를 import하기 전에 진짜 numba 로드가 실패하는 경우에만
@numba.jit을 흉내내는 가짜 numba 모듈을 sys.modules에 먼저 심어서, timing.py의
`import numba`가 이 가짜 모듈을 집어가게 만든다(_install_numba_stub_if_needed) - 아래
_jit 스텁은 데코레이터를 그대로 통과시키기만 하고 실제 JIT 컴파일은 하지 않는데, 우리는
그 함수들을 호출하지 않으니 문제없다. numba가 정상 로드되는 환경(팀원 전원이 이 문제를
겪는 게 아닐 수 있음)에서는 진짜 numba를 그대로 쓴다 - 있으면 쓰고, 막히면 우회하는 방식.

의존성: openai-whisper(STT + 오디오 디코딩), numpy만 사용한다(librosa 제거).
Whisper는 내부적으로 ffmpeg를 호출하므로, 실행 전 ffmpeg가 PATH에 있어야 한다.
"""

import sys
import types
from dataclasses import dataclass, asdict
from functools import lru_cache

import numpy as np

# 2026-08-04: whisper를 파일 맨 위에서 바로 import하면, 이 모듈을 불러오는 순간(=서버
# 기동 시점) whisper가 통째로 로드된다. 실제로 STT/오디오 분석을 쓸 때(_get_whisper_model,
# analyze_voice 호출 시점)까지 import를 미룬다 - 서버는 항상 뜨고, 이 기능을 실제로 호출할
# 때만 (환경이 막혀있다면) 에러가 난다.

# 2026-08-05: base -> small로 변경. 실제 답변 오디오(카카오톡 mp4)로 base/small을
# 직접 비교해보니(ml/debug_transcribe.py) small이 확신도(avg_logprob)도 높고, "통악"->
# "통학", "공정계사원"->"공정개선"처럼 틀린 단어를 바로잡았고, 특히 "4%"->"46%"처럼
# 숫자 자체가 잘못 인식되던 것까지 고쳐졌다(면접 답변에서 성과 수치 오인식은 치명적이라
# 이 차이가 큼). 답변 1건당 추론 시간이 base 대비 +3초 정도 늘지만(2.1초 -> 5.4초,
# CPU 기준), 실시간 스트리밍이 아니라 "답변 끝나면 분석" 방식이라 감수할 만하다고 판단.
WHISPER_MODEL_NAME = "small"
WHISPER_SAMPLE_RATE = 16000  # whisper.audio.load_audio 기본값과 동일하게 맞춤

# 무음 판정 기준(dB, 신호 자체 최대치 대비 상대값). 너무 낮추면 숨소리도 "말하는 중"으로
# 잡히고, 너무 높이면 실제 침묵도 못 잡는다 - librosa 기본 예제들이 흔히 쓰는 30을 그대로
# 유지했다(기존 동작과 최대한 비슷하게 유지하려고).
SILENCE_TOP_DB = 30
# 이 길이(초) 이상 끊기면 "긴 침묵"으로 센다 (짧은 호흡 사이 간격과 구분하기 위함).
LONG_PAUSE_THRESHOLD_SEC = 1.0

# 피치/에너지 프레임 분석 파라미터 - librosa의 기본값(frame_length=2048, hop_length=512)과
# 동일하게 맞춰서 기존 결과와 체감상 비슷한 스케일이 나오도록 했다.
_FRAME_LENGTH = 2048
_HOP_LENGTH = 512
# 사람 목소리 대역. fmin(65.41Hz)은 기존에 쓰던 librosa.note_to_hz("C2")와 동일한 값이라
# 그대로 유지했다. fmax는 원래 note_to_hz("C7")=2093Hz(피아노 음역대에 가까움)를 그대로
# 썼었는데, 실제 사람 발화 음높이는 아무리 높아도 이 근처까지 가지 않는다 - 그리고 이렇게
# 넓게 잡아두면 무음/전환 구간 프레임에서 자기상관 탐색 범위가 tau=7(약 2286Hz) 같은 아주
# 짧은 랙까지 포함돼서, "신호가 급격히 끊기는 경계 프레임"이 만들어내는 인공적인 짧은-랙
# 피크를 실제 피치로 잘못 고르는 경우가 있었다(테스트로 확인됨). 500Hz로 좁혀서 그런
# 비현실적인 짧은 랙 자체를 탐색 범위에서 제외했다 - 부수적으로 오탐도 줄었다.
_PITCH_FMIN_HZ = 65.41
_PITCH_FMAX_HZ = 500.0
# 자기상관 피크가 이 비율(원 신호와의 유사도, 0~1) 밑이면 유성음이 아니라고 보고 버린다 -
# 무음/잡음 구간에서 엉뚱한 피치가 나오는 걸 막는 용도(YIN의 threshold와 같은 역할).
_PITCH_CONFIDENCE_THRESHOLD = 0.3


def _install_numba_stub_if_needed() -> None:
    """whisper를 import하기 전에 반드시 호출한다 - 이유는 위 모듈 docstring의 "2026-08-05
    추가 발견" 참고. 진짜 numba가 정상 로드되면 그대로 두고(return), DLL 차단 등으로 로드
    자체가 실패할 때만 whisper/timing.py의 `import numba`를 가로챌 가짜 모듈을 등록한다.
    가짜 모듈의 jit는 데코레이터를 그대로 통과시키기만 한다(실제 JIT 없음) - timing.py의
    numba.jit 대상 함수(backtrace/dtw_cpu)는 word_timestamps=True일 때만 호출되는데
    우리 코드는 그 옵션을 쓰지 않으므로 안전하다."""
    if "numba" in sys.modules:
        return
    try:
        import numba  # noqa: F401

        return
    except Exception:
        pass  # DLL 차단 등으로 진짜 numba를 못 쓰는 환경 - 아래에서 가짜로 대체

    stub = types.ModuleType("numba")

    def _jit(*args, **kwargs):
        if len(args) == 1 and callable(args[0]) and not kwargs:
            return args[0]

        def _decorator(func):
            return func

        return _decorator

    stub.jit = _jit
    sys.modules["numba"] = stub


@lru_cache(maxsize=1)
def _get_whisper_model():
    """프로세스당 한 번만 로드해서 재사용한다 (요청마다 로드하면 매번 몇 초씩 걸림)."""
    _install_numba_stub_if_needed()
    import whisper

    return whisper.load_model(WHISPER_MODEL_NAME)


def _load_audio_mono16k(audio_path: str) -> np.ndarray:
    """webm/mp4/wav 등 어떤 포맷이든 whisper가 내부적으로 쓰는 ffmpeg 디코더로 16kHz
    모노 float32 PCM 배열로 바꾼다. librosa.load 대신 이걸 쓰는 이유는 위 모듈 docstring
    참고 - numba 의존성을 피하기 위함. `from whisper.audio import load_audio`도 whisper
    패키지 초기화를 거치므로(=timing.py의 numba import를 탄다) 여기서도 스텁 설치가
    먼저 필요하다."""
    _install_numba_stub_if_needed()
    from whisper.audio import load_audio

    return load_audio(audio_path, sr=WHISPER_SAMPLE_RATE)


@dataclass
class TranscriptionResult:
    text: str
    # 2026-08-05 추가: whisper가 인식 결과에 확신이 낮았는지(=환각/오인식 가능성 높음)를
    # 나타내는 참고 신호. avg_logprob은 whisper 내부적으로도 재시도(temperature fallback)
    # 판단에 쓰는 값이라 "공식 신뢰도 점수"는 아니지만, 커뮤니티에서 흔히 쓰는 경험적
    # 기준이다 - 100% 정확한 판정이 아니라 "이 결과는 못 믿을 수도 있다"는 참고용 신호로만
    # 쓴다(그래서 이름도 confidence_score가 아니라 low_confidence 불리언으로 뒀다 - 숫자로
    # 주면 확신도 점수처럼 오해될 수 있어서).
    low_confidence: bool

    def to_dict(self) -> dict:
        return asdict(self)


# STT에 문맥을 미리 알려주면(특히 whisper처럼 문맥 조건부 생성 모델은) 인식 방향이 좀 더
# 안정된다 - "이건 면접 답변 오디오다"라는 걸 미리 알려주는 정도의 가벼운 힌트.
_TRANSCRIBE_INITIAL_PROMPT = "채용 면접에서 지원자가 자연스러운 한국어 구어체로 답변하는 내용입니다."

# whisper 커뮤니티에서 경험적으로 쓰이는 기준값 - avg_logprob(세그먼트별 평균 로그 확률)이
# 이보다 낮으면(더 음수) 모델이 그 구간 인식에 확신이 낮았다는 뜻. 공식 API가 보장하는 값은
# 아니라서 "확실히 틀렸다"가 아니라 "믿을 만큼 확신하지 못했다" 정도의 참고 신호로만 쓴다.
_LOW_CONFIDENCE_AVG_LOGPROB_THRESHOLD = -1.0


def _trim_silence(y: np.ndarray, sr: int) -> np.ndarray:
    """STT(whisper)에 넘기기 전에 답변 맨 앞/맨 뒤의 무음만 잘라낸다 - 중간에 있는 긴
    침묵은 안 건드린다(analyze_voice의 긴 침묵 지표와는 무관한, whisper가 무음 구간에서
    환각 텍스트를 만들어내는 경향을 줄이려는 목적일 뿐이라 범위를 앞뒤로만 한정했다).
    말이 시작/끝나는 지점이 딱 붙어서 잘리지 않도록 짧게 패딩을 남긴다."""
    rms = _frame_rms(y)
    if rms.size == 0:
        return y
    peak = np.max(rms)
    if peak <= 0:
        return y
    with np.errstate(divide="ignore"):
        db_rel = 20 * np.log10(np.maximum(rms, 1e-10) / peak)
    speaking = np.where(db_rel > -SILENCE_TOP_DB)[0]
    if speaking.size == 0:
        return y  # 전부 무음처럼 보이면 섣불리 자르지 않고 원본 그대로 넘긴다

    frame_sec = _HOP_LENGTH / WHISPER_SAMPLE_RATE
    pad_sec = 0.3
    start_sec = max(0.0, speaking[0] * frame_sec - pad_sec)
    end_sec = min(len(y) / sr, speaking[-1] * frame_sec + _FRAME_LENGTH / WHISPER_SAMPLE_RATE + pad_sec)
    return y[int(start_sec * sr) : int(end_sec * sr)]


def transcribe(audio_path: str, language: str = "ko") -> TranscriptionResult:
    model = _get_whisper_model()
    y = _load_audio_mono16k(audio_path)
    trimmed = _trim_silence(y, WHISPER_SAMPLE_RATE)
    result = model.transcribe(trimmed, language=language, initial_prompt=_TRANSCRIBE_INITIAL_PROMPT)

    segments = result.get("segments") or []
    avg_logprobs = [s["avg_logprob"] for s in segments if "avg_logprob" in s]
    text = result["text"].strip()
    low_confidence = (
        not text
        or not segments
        or (bool(avg_logprobs) and sum(avg_logprobs) / len(avg_logprobs) < _LOW_CONFIDENCE_AVG_LOGPROB_THRESHOLD)
    )
    return TranscriptionResult(text=text, low_confidence=bool(low_confidence))


@dataclass
class VoiceMetrics:
    duration_sec: float
    speaking_rate_chars_per_min: float | None
    pitch_mean_hz: float | None
    pitch_variation_hz: float | None
    silence_ratio: float
    long_pause_count: int
    volume_mean_rms: float
    volume_variation_rms: float

    def to_dict(self) -> dict:
        return asdict(self)


def _frame_starts(num_samples: int) -> range:
    """프레임 시작 인덱스들 - 마지막에 프레임 길이만큼 안 남으면 그 프레임은 버린다."""
    return range(0, max(0, num_samples - _FRAME_LENGTH + 1), _HOP_LENGTH)


def _frame_rms(y: np.ndarray) -> np.ndarray:
    """librosa.feature.rms와 동일한 프레임 파라미터로 프레임별 RMS(에너지)를 계산한다.
    numba 없이 순수 numpy 연산(제곱 -> 평균 -> 제곱근)이라 원래도 가벼운 연산이었다."""
    starts = list(_frame_starts(len(y)))
    if not starts:
        return np.array([np.sqrt(np.mean(y**2))]) if len(y) > 0 else np.array([0.0])
    return np.array([np.sqrt(np.mean(y[s : s + _FRAME_LENGTH] ** 2)) for s in starts])


def _frame_pitch(frame: np.ndarray, sr: int) -> float | None:
    """정규화 자기상관 기반 피치(f0) 추정 - librosa.yin(numba 필요) 대신 쓰는 numpy 전용
    구현. YIN만큼 정교하진 않지만, "말투의 음높이 변동 추세"를 보여주는 용도로는 충분하다."""
    windowed = frame * np.hanning(len(frame))
    if np.max(np.abs(windowed)) < 1e-4:
        return None  # 사실상 무음 프레임

    # np.correlate(mode="full")은 -N+1 ~ N-1 랙까지의 자기상관을 전부 계산한다 - 그중
    # lag>=0 부분만 쓴다(신호가 자기 자신이라 좌우 대칭이라 뒷부분만 봐도 충분함).
    corr = np.correlate(windowed, windowed, mode="full")
    corr = corr[corr.size // 2 :]
    if corr[0] <= 0:
        return None

    tau_min = max(1, int(sr / _PITCH_FMAX_HZ))
    tau_max = min(len(corr) - 1, int(sr / _PITCH_FMIN_HZ))
    if tau_max <= tau_min:
        return None

    segment = corr[tau_min : tau_max + 1]
    if segment.size == 0:
        return None

    peak_offset = int(np.argmax(segment))
    peak_tau = tau_min + peak_offset
    # 원 신호(lag=0)와의 유사도가 낮으면 뚜렷한 주기성이 없다는 뜻 - 유성음이 아닐
    # 가능성이 높아서(자음/잡음/무음) 버린다.
    if corr[peak_tau] / corr[0] < _PITCH_CONFIDENCE_THRESHOLD:
        return None

    return sr / peak_tau


def _pitch_stats(y: np.ndarray, sr: int) -> tuple[float | None, float | None]:
    """프레임 단위로 피치를 뽑아 평균/표준편차를 낸다. 유효한 프레임이 하나도 없으면
    (예: 무음뿐인 파일) None."""
    pitches = [p for start in _frame_starts(len(y)) if (p := _frame_pitch(y[start : start + _FRAME_LENGTH], sr)) is not None]
    if not pitches:
        return None, None
    arr = np.array(pitches)
    return float(np.mean(arr)), float(np.std(arr))


def _silence_stats(rms: np.ndarray, total_duration_sec: float) -> tuple[float, int]:
    """프레임별 RMS를 dB(프레임 중 최대치 대비 상대값)로 바꿔서 SILENCE_TOP_DB 기준으로
    "말하는 프레임"을 가른 뒤, 연속된 프레임들을 (시작 초, 끝 초) 구간으로 묶는다 -
    librosa.effects.split이 반환하던 구간 목록과 같은 역할. 프레임 개수만 세는 대신 실제
    구간(초)으로 계산해야 정밀도가 떨어지지 않는다(프레임 하나가 hop만큼만이 아니라
    frame_length만큼의 오디오를 대표하기 때문).

    긴 침묵 판정은 원래 로직(librosa 버전)과 동일하게 "말하는 구간과 구간 사이의 간격"만
    센다 - 맨 앞(말하기 전 무음)이나 맨 뒤(답변 끝난 뒤 무음)는 포함하지 않는다."""
    if rms.size == 0:
        return 1.0, 0

    peak = np.max(rms)
    if peak <= 0:
        return 1.0, 0  # 전부 무음

    with np.errstate(divide="ignore"):
        db_rel = 20 * np.log10(np.maximum(rms, 1e-10) / peak)
    speaking_frames = db_rel > -SILENCE_TOP_DB

    frame_sec = _HOP_LENGTH / WHISPER_SAMPLE_RATE
    intervals: list[tuple[float, float]] = []
    run_start_frame: int | None = None
    for i, is_speaking in enumerate(speaking_frames):
        if is_speaking and run_start_frame is None:
            run_start_frame = i
        elif not is_speaking and run_start_frame is not None:
            start_sec = run_start_frame * frame_sec
            end_sec = min(total_duration_sec, (i - 1) * frame_sec + _FRAME_LENGTH / WHISPER_SAMPLE_RATE)
            intervals.append((start_sec, end_sec))
            run_start_frame = None
    if run_start_frame is not None:
        start_sec = run_start_frame * frame_sec
        end_sec = min(total_duration_sec, (len(speaking_frames) - 1) * frame_sec + _FRAME_LENGTH / WHISPER_SAMPLE_RATE)
        intervals.append((start_sec, end_sec))

    if not intervals:
        return 1.0, 0

    speaking_sec = sum(end - start for start, end in intervals)
    silence_ratio = max(0.0, 1.0 - (speaking_sec / total_duration_sec)) if total_duration_sec > 0 else 1.0

    long_pause_count = 0
    for (_, prev_end), (next_start, _) in zip(intervals, intervals[1:]):
        if next_start - prev_end >= LONG_PAUSE_THRESHOLD_SEC:
            long_pause_count += 1

    return silence_ratio, long_pause_count


def analyze_voice(audio_path: str, transcript: str) -> VoiceMetrics:
    y = _load_audio_mono16k(audio_path)
    sr = WHISPER_SAMPLE_RATE
    duration_sec = len(y) / sr if sr > 0 else 0.0

    pitch_mean, pitch_variation = _pitch_stats(y, sr)

    rms = _frame_rms(y)
    silence_ratio, long_pause_count = _silence_stats(rms, duration_sec)
    volume_mean = float(np.mean(rms))
    volume_variation = float(np.std(rms))

    char_count = len(transcript.replace(" ", ""))
    speaking_rate = (char_count / duration_sec) * 60 if duration_sec > 0 else None

    return VoiceMetrics(
        duration_sec=round(duration_sec, 2),
        speaking_rate_chars_per_min=round(speaking_rate, 1) if speaking_rate is not None else None,
        pitch_mean_hz=round(pitch_mean, 1) if pitch_mean is not None else None,
        pitch_variation_hz=round(pitch_variation, 1) if pitch_variation is not None else None,
        silence_ratio=round(silence_ratio, 3),
        long_pause_count=long_pause_count,
        volume_mean_rms=round(volume_mean, 4),
        volume_variation_rms=round(volume_variation, 4),
    )
