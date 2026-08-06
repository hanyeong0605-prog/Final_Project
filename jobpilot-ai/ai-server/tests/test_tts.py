"""tts.py 단위 테스트.

Google Cloud TTS를 실제로 호출하지는 않는다(과금/네트워크) - 키 없을 때의 fail-open용
RuntimeError, voice_id 매핑, requests 호출 파라미터가 예상대로 구성되는지만 확인한다.
"""

import base64
from unittest.mock import Mock, patch

import pytest

from app.domain.interview import tts
from app.domain.interview.tts import DEFAULT_VOICE_ID, VOICE_OPTIONS, synthesize_speech


def test_no_api_key_raises_runtime_error(monkeypatch):
    """GOOGLE_TTS_API_KEY가 없으면 requests를 아예 호출하지 않고 RuntimeError를 던져야
    한다 - router.py가 이걸 503으로 바꿔서 돌려주고 프론트가 브라우저 TTS로 폴백한다."""
    monkeypatch.setattr(tts.settings, "google_tts_api_key", "")
    with pytest.raises(RuntimeError):
        synthesize_speech("안녕하세요")


def test_empty_text_raises_value_error(monkeypatch):
    monkeypatch.setattr(tts.settings, "google_tts_api_key", "fake-key")
    with pytest.raises(ValueError):
        synthesize_speech("   ")


def test_synthesize_calls_google_api_with_expected_voice(monkeypatch):
    """voice_id -> google_voice_name 매핑이 실제 요청 body에 정확히 들어가는지 확인한다."""
    monkeypatch.setattr(tts.settings, "google_tts_api_key", "fake-key")

    fake_audio_bytes = b"fake-mp3-bytes"
    fake_response = Mock()
    fake_response.raise_for_status = Mock()
    fake_response.json.return_value = {"audioContent": base64.b64encode(fake_audio_bytes).decode()}

    with patch("requests.post", return_value=fake_response) as mock_post:
        result = synthesize_speech("자기소개를 해주세요", voice_id="ko-c-neural")

    assert result == fake_audio_bytes
    _, kwargs = mock_post.call_args
    assert kwargs["params"] == {"key": "fake-key"}
    assert kwargs["json"]["voice"]["name"] == "ko-KR-Neural2-C"
    assert kwargs["json"]["voice"]["languageCode"] == "ko-KR"
    assert kwargs["json"]["input"]["text"] == "자기소개를 해주세요"


def test_unknown_voice_id_falls_back_to_default(monkeypatch):
    """프론트가 구버전에 캐시된 voice_id를 보내는 등 목록에 없는 값이 와도 에러 없이
    기본 음성으로 대체해야 한다(답변 낭독 자체가 막히는 것보다 낫다)."""
    monkeypatch.setattr(tts.settings, "google_tts_api_key", "fake-key")

    fake_response = Mock()
    fake_response.raise_for_status = Mock()
    fake_response.json.return_value = {"audioContent": base64.b64encode(b"x").decode()}

    with patch("requests.post", return_value=fake_response) as mock_post:
        synthesize_speech("테스트", voice_id="존재하지-않는-id")

    _, kwargs = mock_post.call_args
    default_voice = next(v for v in VOICE_OPTIONS if v.id == DEFAULT_VOICE_ID)
    assert kwargs["json"]["voice"]["name"] == default_voice.google_voice_name


def test_missing_audio_content_raises_runtime_error(monkeypatch):
    monkeypatch.setattr(tts.settings, "google_tts_api_key", "fake-key")

    fake_response = Mock()
    fake_response.raise_for_status = Mock()
    fake_response.json.return_value = {}

    with patch("requests.post", return_value=fake_response):
        with pytest.raises(RuntimeError):
            synthesize_speech("테스트")
