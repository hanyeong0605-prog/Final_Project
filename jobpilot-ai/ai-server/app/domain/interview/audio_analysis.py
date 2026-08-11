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
numba의 네이티브 DLL이 서명 미확인 상태라 Windows 스마트 앱 컨트롤이 로드 자체를 차단하는
환경이 실제로 있었다(팀원 PC에서 확인). librosa 의존성 자체를 없애고 numpy만으로 다시 짰다.

정확도는 librosa.yin(YIN 알고리즘)보다 단순한 정규화 자기상관 방식이라 약간 떨어지지만,
이 기능의 목적 자체가 "정밀한 피치 트래커"가 아니라 "말투의 경향을 보여주는 참고 지표"라서
이 정도 단순화는 목적에 맞다고 판단했다.

2026-08-07 재작성: STT를 openai-whisper(로컬 CPU 추론) 대신 Google Cloud Speech-to-Text
(REST API, google_stt_api_key 인증 - tts.py와 동일한 방식)로 교체했다. 이유:
1) EC2 프리티어에 실제 배포해보니 whisper+torch 패키지가 무거워서(디스크 용량 부족으로
   배포 자체가 실패하는 사고가 있었음) 디스크/메모리 부담이 컸다.
2) whisper의 "small" 사이즈는 리소스 제약 때문에 일부러 줄인 다국어 범용 모델이라, 한국어
   전용으로 튜닝된 구글 모델보다 정확도가 떨어질 가능성이 높다(격음 오인식 등 실제로 겪은
   문제들 - "통악"->"통학" 같은 사례, ml/debug_transcribe.py 참고).
이 교체로 whisper/numba 관련 워크어라운드(_install_numba_stub_if_needed 등)가 전부
필요 없어졌다 - openai-whisper 패키지 자체를 뺐다. 오디오 디코딩(webm/mp4 등 -> 16kHz
모노 PCM)은 whisper.audio.load_audio()가 하던 것과 동일한 ffmpeg 서브프로세스 호출을
직접 구현해서 대체한다(_load_audio_mono16k) - ffmpeg는 Dockerfile에서 이미 설치돼 있고,
pitch/volume 분석(analyze_voice)도 이 함수가 반환하는 PCM 배열을 그대로 계속 쓴다.

의존성: numpy만 사용한다(librosa/openai-whisper 모두 제거). 오디오 디코딩에 ffmpeg가
PATH에 있어야 한다(기존과 동일).
"""

import base64
import subprocess
from dataclasses import dataclass, asdict

import numpy as np
import requests

from app.core.config import settings

AUDIO_SAMPLE_RATE = 16000  # Google STT LINEAR16 권장값(16000이 "최적"이라고 공식 문서에 명시됨)

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


def _load_audio_mono16k(audio_path: str) -> np.ndarray:
    """webm/mp4/wav 등 어떤 포맷이든 ffmpeg로 16kHz 모노 float32 PCM 배열로 바꾼다.

    2026-08-07: openai-whisper의 `whisper.audio.load_audio()`가 내부적으로 하던 것과
    동일한 ffmpeg 서브프로세스 호출을 직접 구현한 것 - whisper 패키지를 뺐지만 이 디코딩
    로직 자체는 그대로 필요해서(analyze_voice의 pitch/volume 분석이 이 PCM 배열을 씀)
    가져왔다. ffmpeg는 Dockerfile에 이미 설치돼 있다."""
    cmd = [
        "ffmpeg",
        "-nostdin",
        "-threads",
        "0",
        "-i",
        audio_path,
        "-f",
        "s16le",
        "-ac",
        "1",
        "-acodec",
        "pcm_s16le",
        "-ar",
        str(AUDIO_SAMPLE_RATE),
        "-",
    ]
    try:
        completed = subprocess.run(cmd, capture_output=True, check=True)
    except subprocess.CalledProcessError as exc:
        stderr = exc.stderr.decode("utf-8", errors="ignore") if exc.stderr else ""
        raise RuntimeError(f"오디오 디코딩에 실패했습니다: {stderr}") from exc
    except FileNotFoundError as exc:
        raise RuntimeError("ffmpeg를 찾을 수 없습니다 - PATH를 확인하세요.") from exc

    return np.frombuffer(completed.stdout, dtype=np.int16).astype(np.float32) / 32768.0


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


_STT_RECOGNIZE_URL = "https://speech.googleapis.com/v1/speech:recognize"
_STT_TIMEOUT_SEC = 30

# Google STT의 confidence는 0.0~1.0 스케일(공식 문서: "높을수록 인식 결과가 맞을 가능성이
# 크다는 추정치"이고 "보장된 값은 아니다"라고 명시돼 있음 - 그래서 whisper의 avg_logprob
# 때와 마찬가지로 "참고 신호"로만 쓴다). 이 밑이면 low_confidence로 표시한다 - 임의로 정한
# 값이라 실사용 데이터를 더 모으면 조정할 수 있다.
_LOW_CONFIDENCE_THRESHOLD = 0.6


def _detect_speech_frames(y: np.ndarray) -> np.ndarray:
    """RMS 기준으로 "말소리가 있다고 보이는" 프레임 인덱스를 돌려준다(SILENCE_TOP_DB 기준
    - 전체 클립 중 최대 음량 대비 상대적으로 조용한 구간은 무음으로 취급). 빈 배열이면
    클립 전체가 무음에 가깝다는 뜻 - _trim_silence와 transcribe()의 환각 방지 체크가
    같이 쓴다."""
    rms = _frame_rms(y)
    if rms.size == 0:
        return np.array([], dtype=int)
    peak = np.max(rms)
    if peak <= 0:
        return np.array([], dtype=int)
    with np.errstate(divide="ignore"):
        db_rel = 20 * np.log10(np.maximum(rms, 1e-10) / peak)
    return np.where(db_rel > -SILENCE_TOP_DB)[0]


def _trim_silence(y: np.ndarray, sr: int) -> np.ndarray:
    """STT에 넘기기 전에 답변 맨 앞/맨 뒤의 무음만 잘라낸다 - 중간에 있는 긴 침묵은 안
    건드린다(analyze_voice의 긴 침묵 지표와는 무관, STT 모델이 무음 구간에서 엉뚱한 텍스트를
    만들어내는 경향을 줄이려는 목적일 뿐이라 범위를 앞뒤로만 한정했다). 말이 시작/끝나는
    지점이 딱 붙어서 잘리지 않도록 짧게 패딩을 남긴다."""
    speaking = _detect_speech_frames(y)
    if speaking.size == 0:
        return y  # 전부 무음처럼 보이면 섣불리 자르지 않고 원본 그대로 넘긴다(호출부에서 별도 처리)

    frame_sec = _HOP_LENGTH / AUDIO_SAMPLE_RATE
    pad_sec = 0.3
    start_sec = max(0.0, speaking[0] * frame_sec - pad_sec)
    end_sec = min(len(y) / sr, speaking[-1] * frame_sec + _FRAME_LENGTH / AUDIO_SAMPLE_RATE + pad_sec)
    return y[int(start_sec * sr) : int(end_sec * sr)]


def _gemini_correct_transcript(text: str) -> str:
    """STT 확신도가 낮았던(low_confidence) 답변에 한해서만 호출한다 - Whisper가 한국어
    격음('ㅎ/ㅋ/ㅌ/ㅍ' 등)을 다른 자음으로 잘못 인식하는 경우가 흔해서, 명백한 오인식으로
    보이는 단어만 Gemini에게 최소한으로 교정해달라고 요청한다. question_generator.py의
    _gemini_polish와 같은 fail-open 원칙 - 키가 없거나 호출이 실패하면 원문 그대로 반환한다
    (STT 결과가 아예 안 나오는 것보단 원문이라도 있는 게 낫다). 답변 "내용" 자체를 바꾸는 게
    아니라 "오인식으로 보이는 단어 교정"만 하도록 프롬프트로 강하게 제한한다 - 안 그러면
    evaluation.py가 실제로 지원자가 말하지 않은 내용을 근거로 채점하게 될 위험이 있다.

    2026-08-05: low_confidence일 때만 호출해서(항상 켜두지 않음) 비용을 최소화한다 - 이미
    확신도 높은 결과까지 매번 다듬을 필요는 없다."""
    if not settings.gemini_api_key:
        return text
    try:
        from google import genai

        client = genai.Client(api_key=settings.gemini_api_key)
        prompt = (
            "다음은 한국어 채용면접 답변을 음성 인식(STT)으로 변환한 텍스트인데, 인식 확신도가 "
            "낮았던 구간이 있다. 특히 'ㅎ/ㅋ/ㅌ/ㅍ' 같은 격음이 다른 자음으로 잘못 인식되는 "
            "경우가 흔하다.\n\n"
            "아래 규칙을 반드시 지켜라.\n"
            "1) 명백히 발음 인식 오류로 보이는 단어(문맥상 말이 안 되는 단어)만 자연스러운 "
            "단어로 최소한으로 고쳐라\n"
            "2) 문장 구조, 어순, 내용, 의미는 절대 바꾸지 마라 - 새로운 정보를 추가하거나 "
            "빼지 마라\n"
            "3) 이미 자연스러운 부분은 절대 손대지 마라\n"
            "4) 고칠 부분이 없으면 원문을 그대로 반환해라\n"
            "5) 결과는 교정된 텍스트만 출력해라 - 설명, 따옴표, 다른 말은 절대 붙이지 마라\n\n"
            f"STT 결과: {text}"
        )
        response = client.models.generate_content(model=settings.gemini_model, contents=prompt)
        corrected = (response.text or "").strip()
        return corrected or text
    except Exception:
        return text


def _pcm16_bytes(y: np.ndarray) -> bytes:
    """float32(-1.0~1.0) PCM 배열을 Google STT가 요구하는 LINEAR16(부호 있는 16비트) raw
    바이트로 되돌린다 - _load_audio_mono16k가 원래 이 스케일로 정규화했던 걸 역변환."""
    clipped = np.clip(y * 32768.0, -32768, 32767)
    return clipped.astype(np.int16).tobytes()


def transcribe(audio_path: str, language: str = "ko-KR") -> TranscriptionResult:
    """Google Cloud Speech-to-Text(동기 recognize)로 답변 오디오를 텍스트로 바꾼다.

    2026-08-07: openai-whisper 로컬 추론에서 교체 - 이유는 모듈 docstring 참고. 동기
    recognize는 60초/10MB 제한이 있다(공식 문서 명시) - 면접 답변 권장 길이가 30~60초라
    보통은 문제없지만, 사용자가 그보다 훨씬 길게 답하면 API가 에러를 반환할 수 있다. 그런
    경우도 다른 실패와 마찬가지로 fail-open(빈 텍스트 + low_confidence=True)으로 처리한다 -
    긴 답변 지원(비동기 longrunningrecognize)까지는 이번에 안 하고, 있는 그대로도 대부분의
    실사용 케이스는 커버되므로 이후 필요성이 확인되면 별도로 붙인다."""
    y = _load_audio_mono16k(audio_path)

    # 2026-08-06: 실제로 겪은 버그 - 답변 오디오에 말소리가 거의/전혀 없으면(마이크에 소리가
    # 안 잡혔거나, 답변을 안 하고 바로 답변 완료를 누른 경우 등) STT가 엉뚱한 텍스트를
    # 만들어내는(환각) 경우가 있었다. 그래서 말소리 자체가 감지 안 되면 API를 아예 호출하지
    # 않고 빈 결과를 바로 반환한다 - 없는 답변을 지어내는 것보다 "인식된 내용 없음"이 낫다.
    if _detect_speech_frames(y).size == 0:
        return TranscriptionResult(text="", low_confidence=True)

    if not settings.google_stt_api_key:
        # fail-open이지만 무음 케이스와 결과를 구분할 방법이 없다 - 둘 다 "텍스트 없음"으로
        # 처리해도 호출부(evaluation.py 등) 입장에서는 동일하게 다뤄야 하는 상황이라 문제없다.
        return TranscriptionResult(text="", low_confidence=True)

    trimmed = _trim_silence(y, AUDIO_SAMPLE_RATE)
    audio_content_b64 = base64.b64encode(_pcm16_bytes(trimmed)).decode("ascii")

    try:
        response = requests.post(
            _STT_RECOGNIZE_URL,
            params={"key": settings.google_stt_api_key},
            json={
                "config": {
                    "encoding": "LINEAR16",
                    "sampleRateHertz": AUDIO_SAMPLE_RATE,
                    "languageCode": language,
                    "enableAutomaticPunctuation": True,
                },
                "audio": {"content": audio_content_b64},
            },
            timeout=_STT_TIMEOUT_SEC,
        )
        response.raise_for_status()
        data = response.json()
    except Exception:
        # 네트워크 오류, 60초/10MB 초과로 인한 4xx, 그 외 예상 못 한 응답 전부 여기서
        # 잡는다 - STT 실패가 전체 답변 분석 흐름을 죽이면 안 된다(fail-open).
        return TranscriptionResult(text="", low_confidence=True)

    results = data.get("results") or []
    # SpeechRecognitionAlternative.transcript는 "각 결과를 구분자 없이 이어붙이면 전체
    # 텍스트가 된다"고 공식 문서에 명시돼 있다(첫 결과가 아니면 앞에 공백이 이미 포함됨).
    transcripts: list[str] = []
    confidences: list[float] = []
    for result in results:
        alternatives = result.get("alternatives") or []
        if not alternatives:
            continue
        top = alternatives[0]
        transcripts.append(top.get("transcript", ""))
        # confidence 기본값 0.0은 "설정 안 됨"을 뜻하는 sentinel이라고 문서에 명시돼 있어서
        # 평균 계산에서 제외한다(0.0을 실제로 낮은 확신도로 취급하면 왜곡됨).
        confidence = top.get("confidence")
        if confidence:
            confidences.append(confidence)

    text = "".join(transcripts).strip()
    low_confidence = (
        not text or not confidences or (sum(confidences) / len(confidences)) < _LOW_CONFIDENCE_THRESHOLD
    )
    # low_confidence여도 UI에는 그대로 경고를 보여준다(원문이 그대로인지 Gemini가 교정한
    # 텍스트인지와 무관하게, STT 자체가 확신하지 못했던 답변이라는 사실은 변하지 않음 -
    # Gemini 교정도 완벽을 보장하진 않으므로 "믿을 만큼 확신하지 못했다"는 신호를 계속 보여준다).
    if low_confidence and text:
        text = _gemini_correct_transcript(text)
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

    frame_sec = _HOP_LENGTH / AUDIO_SAMPLE_RATE
    intervals: list[tuple[float, float]] = []
    run_start_frame: int | None = None
    for i, is_speaking in enumerate(speaking_frames):
        if is_speaking and run_start_frame is None:
            run_start_frame = i
        elif not is_speaking and run_start_frame is not None:
            start_sec = run_start_frame * frame_sec
            end_sec = min(total_duration_sec, (i - 1) * frame_sec + _FRAME_LENGTH / AUDIO_SAMPLE_RATE)
            intervals.append((start_sec, end_sec))
            run_start_frame = None
    if run_start_frame is not None:
        start_sec = run_start_frame * frame_sec
        end_sec = min(total_duration_sec, (len(speaking_frames) - 1) * frame_sec + _FRAME_LENGTH / AUDIO_SAMPLE_RATE)
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
    sr = AUDIO_SAMPLE_RATE
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
