"""audio_analysis.py의 _gemini_correct_transcript, transcribe() 무음 가드 단위 테스트.

Whisper 모델 로딩 자체(실제 STT 추론)는 무겁고 이 테스트의 목적과 무관해서 다루지 않는다 -
_get_whisper_model/_load_audio_mono16k를 모킹해서 whisper 내부를 타지 않게 한다.
"""

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


def test_transcribe_returns_empty_without_calling_whisper_when_silent(monkeypatch):
    """핵심 회귀 테스트 - 오디오가 무음이면 whisper 모델을 아예 호출하지 않고(환각 방지)
    바로 빈 텍스트 + low_confidence=True를 반환해야 한다."""
    silence = np.zeros(16000 * 2, dtype=np.float32)
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: silence)

    whisper_model_getter = Mock(side_effect=AssertionError("무음인데 whisper 모델을 호출하면 안 된다"))
    monkeypatch.setattr(audio_analysis, "_get_whisper_model", whisper_model_getter)

    result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == ""
    assert result.low_confidence is True
    whisper_model_getter.assert_not_called()


def test_transcribe_calls_whisper_when_speech_detected(monkeypatch):
    """반대로 말소리가 감지되면 정상적으로 whisper까지 호출돼야 한다(가드가 정상 케이스까지
    막아버리는 회귀가 없는지 확인)."""
    t = np.linspace(0, 1, 16000, dtype=np.float32)
    loud = (0.8 * np.sin(2 * np.pi * 200 * t)).astype(np.float32)
    monkeypatch.setattr(audio_analysis, "_load_audio_mono16k", lambda path: loud)

    fake_model = Mock()
    fake_model.transcribe.return_value = {
        "text": "안녕하세요 반갑습니다",
        "segments": [{"avg_logprob": -0.2}],
    }
    monkeypatch.setattr(audio_analysis, "_get_whisper_model", lambda: fake_model)

    result = audio_analysis.transcribe("dummy_path.webm")

    assert result.text == "안녕하세요 반갑습니다"
    fake_model.transcribe.assert_called_once()
