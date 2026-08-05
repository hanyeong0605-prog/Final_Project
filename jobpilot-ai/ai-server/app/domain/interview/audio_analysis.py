"""모의면접 답변 오디오 분석.

2026-08-03 설계 메모: "표정/음성으로 감정·긴장도를 판독한다"는 컨셉은 하지 않는다.
감정 인식은 연구 단계에서도 정확도가 낮고, 확신에 찬 "긴장도 68%" 같은 숫자를 내면
오히려 신뢰도만 떨어뜨린다("애매하게 할 거면 안 하는 게 낫다"는 판단). 대신 근거를
바로 설명할 수 있는 "객관적으로 측정 가능한 지표"만 뽑아서 보여주는 방향으로 잡았다:
  - 말속도 (분당 글자 수)
  - 피치(음높이) 변동폭 - 목소리 떨림의 대리 지표
  - 침묵/멈춤 비율과 긴 침묵 횟수
  - 음량(에너지) 변동폭 - 목소리 떨림의 또 다른 대리 지표
표정/자세는 이후 프론트에서 MediaPipe(Face/Pose Landmark)로 브라우저에서 실시간
측정할 예정이라 이 파일 범위에는 없다 (실시간 영상은 서버로 스트리밍하기엔 무겁고,
브라우저 클라이언트 처리가 더 적합하다고 판단).

의존성: openai-whisper(STT), librosa(음성 특징 추출). 둘 다 requirements.txt에 추가.
Whisper는 내부적으로 ffmpeg를 호출하므로, 실행 전 ffmpeg가 PATH에 있어야 한다.
"""

from dataclasses import dataclass, asdict
from functools import lru_cache

import librosa
import numpy as np

# 2026-08-04: whisper를 파일 맨 위에서 바로 import하면, 이 모듈을 불러오는 순간(=서버
# 기동 시점) whisper -> numba까지 통째로 로드된다. 학원 컴퓨터처럼 애플리케이션 제어
# 정책으로 numba의 네이티브 DLL(_helperlib)이 막힌 환경에서는 이거 하나 때문에
# 크롤러 API처럼 무관한 기능까지 포함해서 서버 전체가 기동을 못 한다. 그래서 whisper는
# 실제로 STT를 쓸 때(_get_whisper_model 호출 시점)까지 import를 미룬다 - 서버는 항상
# 뜨고, 이 기능을 실제로 호출할 때만 (환경이 막혀있다면) 에러가 난다.

# base 모델: CPU에서도 감당 가능한 선에서 한국어 인식 품질이 쓸만한 절충점.
# GPU 없는 노트북 기준 답변 1건(1~2분)당 몇 초~수십 초 정도 걸릴 수 있음 - 실시간
# 스트리밍이 아니라 "답변 끝나면 분석" 방식이라 문제 없다(앞서 논의한 턴제 구조).
WHISPER_MODEL_NAME = "base"

# 무음 판정 기준(dB). 너무 낮추면 숨소리도 "말하는 중"으로 잡히고, 너무 높이면
# 실제 침묵도 못 잡는다 - librosa 기본 예제들이 흔히 쓰는 30을 시작값으로 채택.
SILENCE_TOP_DB = 30
# 이 길이(초) 이상 끊기면 "긴 침묵"으로 센다 (짧은 호흡 사이 간격과 구분하기 위함).
LONG_PAUSE_THRESHOLD_SEC = 1.0


@lru_cache(maxsize=1)
def _get_whisper_model():
    """프로세스당 한 번만 로드해서 재사용한다 (요청마다 로드하면 매번 몇 초씩 걸림)."""
    import whisper

    return whisper.load_model(WHISPER_MODEL_NAME)


def transcribe(audio_path: str, language: str = "ko") -> str:
    model = _get_whisper_model()
    result = model.transcribe(audio_path, language=language)
    return result["text"].strip()


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


def _pitch_stats(y: np.ndarray, sr: int) -> tuple[float | None, float | None]:
    """librosa.yin으로 프레임별 f0(기본 주파수)를 뽑아 평균/표준편차를 낸다.
    무음 구간에서는 yin이 부정확한 값을 뱉을 수 있어, 사람 목소리 대역(C2~C7)
    밖의 값은 걸러낸다. 유효한 프레임이 하나도 없으면(예: 무음뿐인 파일) None."""
    f0 = librosa.yin(y, fmin=librosa.note_to_hz("C2"), fmax=librosa.note_to_hz("C7"), sr=sr)
    voiced = f0[np.isfinite(f0)]
    if voiced.size == 0:
        return None, None
    return float(np.mean(voiced)), float(np.std(voiced))


def _silence_stats(y: np.ndarray, sr: int, total_duration_sec: float) -> tuple[float, int]:
    """말하는 구간(non-silent interval) 목록을 얻어서 침묵 비율과 "긴 침묵" 횟수를 센다."""
    intervals = librosa.effects.split(y, top_db=SILENCE_TOP_DB)
    if len(intervals) == 0:
        # 전부 무음으로 판정된 경우 - 침묵 비율 100%로 처리.
        return 1.0, 0

    speaking_sec = sum((end - start) for start, end in intervals) / sr
    silence_ratio = max(0.0, 1.0 - (speaking_sec / total_duration_sec))

    long_pause_count = 0
    for (prev_start, prev_end), (next_start, _next_end) in zip(intervals, intervals[1:]):
        gap_sec = (next_start - prev_end) / sr
        if gap_sec >= LONG_PAUSE_THRESHOLD_SEC:
            long_pause_count += 1

    return silence_ratio, long_pause_count


def analyze_voice(audio_path: str, transcript: str) -> VoiceMetrics:
    y, sr = librosa.load(audio_path, sr=16000, mono=True)
    duration_sec = librosa.get_duration(y=y, sr=sr)

    pitch_mean, pitch_variation = _pitch_stats(y, sr)
    silence_ratio, long_pause_count = _silence_stats(y, sr, duration_sec)

    rms = librosa.feature.rms(y=y)[0]
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
