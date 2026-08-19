"""audio_analysis.py의 _gemini_correct_transcript, transcribe() 단위 테스트.

2026-08-07: STT가 로컬 whisper에서 Google Cloud Speech-to-Text(REST)로 바뀌면서, 실제
네트워크 호출은 tts.py 테스트와 같은 패턴(requests.post 모킹)으로 대체한다. 오디오 디코딩
(_load_audio_mono16k)은 ffmpeg 서브프로세스를 직접 부르는 순수 함수라 실제 ffmpeg로
합성음(사인파)을 만들어 왕복 검증한다 - 이 샌드박스/CI 이미지 둘 다 ffmpeg가 이미 깔려있다
(Dockerfile, 오디오 분석 기능 자체의 전제조건).
"""

import subprocess
import tempfile
from unittest.mock import Mock, patch

import numpy as np

from app.domain.interview import audio_analysis


def test_no_api_key_returns_original_text(monkeypatch):
    """GEMINI_API_KEY가 없으면 Gemini를 호출하지 않고 원문 그대로(fail-open) 반환해야
    한다 - STT 결과가 아예 안 나오는 것보단 원문이라도 남기는 게 낫다."""
    monkeypatch.setattr(audio_analysis.settings, "gemini_api_key", "")
    text = "이건 원문 그대로 나와야 한다"
    assert audio_analysis._gemini_correct_transcript(text) == text


def test_corrects_transcript_when_key_present(monkeypatch):
    """키가 있으면 Gemini를 호출하고, 응답 텍스트를 그대로 반환해야 한다."""
    monkeypatch.setattr(audio_analysis.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "교정된 텍스트입니다"

    class FakeModels:
        def generate_content(self, model, contents):
            assert "발음 인식 오류" in contents  # 프롬프트에 교정 지시가 들어갔는지 확인
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        result = audio_analysis._gemini_correct_transcript("원문 텍스트")

    assert result == "교정된 텍스트입니다"


def test_falls_back_to_original_on_api_error(monkeypatch):
    """호출 중 예외가 나도(네트워크 오류 등) 예외를 던지지 않고 원문을 그대로 반환해야
    한다(fail-open) - 답변 분석 전체 흐름이 이 단계 때문에 죽으면 안 된다."""
    monkeypatch.setattr(audio_analysis.settings, "gemini_api_key", "fake-key")

    class FakeClient:
        def __init__(self, api_key=None):
            raise RuntimeError("network error")

    with patch("google.genai.Client", FakeClient):
        text = "원문 텍스트"
        assert audio_analysis._gemini_correct_transcript(text) == text


def test_empty_response_falls_back_to_original(monkeypatch):
    """Gemini가 빈 문자열을 반환하면(이론상 드물지만) 빈 텍스트로 덮어쓰지 않고 원문을
    지킨다."""
    monkeypatch.setattr(audio_analysis.settings, "gemini_api_key", "fake-key")

    class FakeResponse:
        text = "   "

    class FakeModels:
        def generate_content(self, model, contents):
            return FakeResponse()

    class FakeClient:
        def __init__(self, api_key=None):
            self.models = FakeModels()

    with patch("google.genai.Client", FakeClient):
        text = "원문 텍스트"
        assert audio_analysis._gemini_correct_transcript(text) == text


# 2026-08-06: 실제로 겪은 버그 재발 방지 - 무음/저음량 오디오에서 whisper가 initial_prompt
# 문구("...자연스러운 한국어 구어체로 답변하는 내용입니다")를 그대로 베껴서 답변인 것처럼
# 지어내는 환각이 있었다. "말소리가 아예 감지 안 되면 whisper를 부르지도 않는다"는 가드를
# 추가했는데, 그 가드가 실제로 동작하는지 확인한다.


def test_detect_speech_frames_empty_for_pure_silence():
    silence = np.zeros(16000 * 2, dtype=np.float32)  # 2초짜리 완전한 무음
    assert audio_analysis._detect_speech_frames(silence).size == 0


def test_detect_speech_frames_nonempty_for_loud_signal():
    # 진폭이 큰 사인파 - 명확히 "말소리 있음"으로 잡혀야 한다.
    t = np.linspace(0, 1, 16000, dtype=np.float32)
    loud = (0.8 * np.sin(2 * np.pi * 200 * t)).astype(np.float32)
    assert audio_analysis._detect_speech_frames(loud).size > 0


# 2026-08-19: 실제로 겪은 버그 재발 방지 - 아이폰 크롬에서 정상적으로 답을 했는데도
# "인식된 내용 없음"이 나온 사례. 녹음 시작 직후 마이크 팝/클릭성 스파이크가 한두 프레임
# 섞이면, np.max(rms) 기준 상대 dB 임계값이 스파이크에 휘둘려서 그보다 훨씬 조용한(하지만
# 실제로 존재하는) 목소리 구간 전체를 "무음"으로 오판했다 - _robust_peak(95th percentile)
# 도입 후에도 이 사례를 못 잡으면 회귀다.
def test_detect_speech_frames_not_fooled_by_transient_spike():
    sr = 16000
    t = np.linspace(0, 2, sr * 2, dtype=np.float32)
    # 2초 중 뒤쪽 1.5초는 평범한 크기(0.15)의 목소리 - 스파이크 없이도 그 자체로는
    # 충분히 "말소리"로 잡힐 크기다.
    speech = (0.15 * np.sin(2 * np.pi * 200 * t)).astype(np.float32)
    # 맨 앞 5ms(80 샘플)에 훨씬 더 큰(1.0) 순간적인 팝/클릭 스파이크를 얹는다 - 목소리보다
    # 20dB 이상 더 큼(마이크 활성화 시점 노이즈를 흉내).
    speech[:80] = 1.0
    frames = audio_analysis._detect_speech_frames(speech)
    assert frames.size > 0
    # 스파이크가 낀 맨 첫 프레임 이후, 뒤쪽(평범한 크기의 목소리 구간)도 "말소리"로
    # 잡혀야 한다 - 스파이크 때문에 그 뒤가 전부 묻혀버리면 회귀.
    frame_sec = audio_analysis._HOP_LENGTH / sr
    assert any(frame_idx * frame_sec > 1.0 for frame_idx in frames)


def _loud_signal() -> np.ndarray:
    t = np.linspace(0, 1, 16000, dtype=np.float32)
    return (0.8 * np.sin(2 * np.pi * 200 * t)).astype(np.float32)


def _fake_stt_response(transcript: str, confidence: float):
    fake_response = Mock()
    fake_response.raise_for_status = Mock()
    fake_response.json.return_value = {
        "results": [{"alternatives": [{"transcript": transcript, "confidence": confidence}]}]
    }
    return fake_response


def test_transcribe_returns_empty_without_calling_api_when_silent(monkeypatch):
    """핵심 회귀 테스트 - 오디오가 무음이면 Google STT API를 아예 호출하지 않고(환각 방지)
    바로 빈 텍스트 + low_confidence=True를 반환해야 한다."""
    silence = np.zeros(16000 * 2, dtype=np.float32)
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: silence)
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "fake-key")

    with patch("requests.post") as mock_post:
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == ""
    assert result.low_confidence is True
    mock_post.assert_not_called()


def test_no_api_key_returns_empty_without_calling_api(monkeypatch):
    """키가 없으면(fail-open) API를 호출하지 않고 빈 텍스트 + low_confidence=True를
    반환해야 한다 - router.py가 이 결과를 받아도 예외 없이 처리할 수 있어야 한다."""
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: _loud_signal())
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "")

    with patch("requests.post") as mock_post:
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == ""
    assert result.low_confidence is True
    mock_post.assert_not_called()


def test_transcribe_calls_google_stt_when_speech_detected(monkeypatch):
    """말소리가 감지되면 Google STT API를 호출하고, 응답의 transcript를 그대로 반환해야
    한다(가드가 정상 케이스까지 막아버리는 회귀가 없는지 확인)."""
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: _loud_signal())
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "fake-key")

    fake_response = _fake_stt_response("안녕하세요 반갑습니다", confidence=0.95)

    with patch("requests.post", return_value=fake_response) as mock_post:
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == "안녕하세요 반갑습니다"
    assert result.low_confidence is False
    mock_post.assert_called_once()
    _, kwargs = mock_post.call_args
    assert kwargs["params"] == {"key": "fake-key"}
    assert kwargs["json"]["config"]["encoding"] == "LINEAR16"
    assert kwargs["json"]["config"]["sampleRateHertz"] == audio_analysis.AUDIO_SAMPLE_RATE
    assert kwargs["json"]["config"]["languageCode"] == "ko-KR"


def test_low_confidence_below_threshold_triggers_gemini_correction(monkeypatch):
    """confidence가 임계값보다 낮으면 low_confidence=True로 표시되고, Gemini 교정이
    시도돼야 한다(_gemini_correct_transcript 호출 여부로 확인)."""
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: _loud_signal())
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "fake-key")

    fake_response = _fake_stt_response("애매한 인식 결과", confidence=0.2)
    correction = Mock(return_value="교정된 결과")
    monkeypatch.setattr(audio_analysis, "_gemini_correct_transcript", correction)

    with patch("requests.post", return_value=fake_response):
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.low_confidence is True
    assert result.text == "교정된 결과"
    correction.assert_called_once_with("애매한 인식 결과")


def test_empty_results_is_low_confidence(monkeypatch):
    """Google STT가 results를 아예 안 주면(인식 실패) low_confidence=True + 빈 텍스트로
    처리해야 한다."""
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: _loud_signal())
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "fake-key")

    fake_response = Mock()
    fake_response.raise_for_status = Mock()
    fake_response.json.return_value = {"results": []}

    with patch("requests.post", return_value=fake_response):
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == ""
    assert result.low_confidence is True


def test_api_failure_is_fail_open(monkeypatch):
    """네트워크 오류나 4xx/5xx(예: 60초 초과로 인한 에러) 등 requests가 예외를 던지는
    모든 경우에 fail-open으로 빈 텍스트 + low_confidence=True를 반환해야 한다 - 답변 분석
    전체 흐름이 STT 실패 때문에 죽으면 안 된다."""
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: _loud_signal())
    monkeypatch.setattr(audio_analysis.settings, "google_stt_api_key", "fake-key")

    with patch("requests.post", side_effect=RuntimeError("network down")):
        result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == ""
    assert result.low_confidence is True


def test_load_audio_mono16k_decodes_real_wav_via_ffmpeg():
    """_load_audio_mono16k는 whisper 없이 ffmpeg 서브프로세스를 직접 호출한다 - 실제
    ffmpeg로 만든 짧은 사인파 WAV를 디코딩해서 예상한 길이/타입의 배열이 나오는지 확인한다
    (모킹이 아니라 실제 왕복 검증 - 이 함수 자체가 순수 subprocess 호출이라 모킹하면 로직을
    검증하는 의미가 없다)."""
    with tempfile.NamedTemporaryFile(suffix=".wav") as f:
        subprocess.run(
            [
                "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=200:duration=1",
                "-ar", "48000", "-ac", "2", f.name,
            ],
            capture_output=True,
            check=True,
        )
        y = audio_analysis._load_audio_mono16k(f.name)

    assert y.dtype == np.float32
    # 1초짜리 오디오를 16kHz 모노로 디코딩했으니 대략 16000 샘플이어야 한다(리샘플링
    # 오차로 정확히 일치하지 않을 수 있어 근사치로 확인).
    assert abs(len(y) - 16000) < 100
    # ffmpeg lavfi sine 소스 기본 진폭이 크지 않아(실측 ~0.09) 낮은 임계값으로 확인한다 -
    # 목적은 "완전한 무음(전부 0)이 아니라 실제 신호가 들어있는지"뿐이라 이걸로 충분하다.
    assert np.max(np.abs(y)) > 0.01
