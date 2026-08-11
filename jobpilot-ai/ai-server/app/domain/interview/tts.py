"""모의면접 질문 낭독 - Google Cloud Text-to-Speech 연동.

2026-08-06 설계 메모:
- 브라우저 기본 TTS(SpeechSynthesisUtterance)는 설치된 OS 음성에 따라 기계음성 느낌이 강해서
  "귀엽고 자연스러운 목소리로 골라 쓰고 싶다"는 요청으로 이 모듈을 추가했다. Google Cloud
  Text-to-Speech REST API를 그대로 호출한다(공식 SDK 대신 requests로 직접 호출 - 이미
  requirements.txt에 있는 라이브러리만 써서 새 의존성을 안 늘리려는 목적, 다른 서버 호출도
  이 프로젝트에서 requests를 씀).
- 인증은 API 키 방식(쿼리 파라미터 `key=...`)을 쓴다 - 서비스 계정 JSON보다 로컬 개발 환경에
  올리기 훨씬 간단하다(환경변수 하나만 있으면 됨).
- 키가 없으면 RuntimeError를 던진다(fail-open은 호출부/프론트 책임) - router.py가 이걸 503으로
  바꿔서 돌려주고, 프론트는 그 응답을 보고 브라우저 기본 TTS로 자동 폴백한다(evaluation.py의
  Gemini 키 없을 때 패턴과 동일한 설계).
- 정확한 한국어(ko-KR) 음성 이름 전체 목록은 Google 문서 표가 너무 커서 이 자리에서 전수
  확인은 못 했다 - 검색으로 확인된 ko-KR-Neural2-A(여성)/ko-KR-Neural2-C(남성)와, 모든
  로케일에 공통으로 존재하는 Standard-A~D 명명 규칙을 사용했다. 만약 특정 voice id가 400을
  반환하면(음성 이름이 실제로 없는 경우) Cloud Console > Text-to-Speech > 음성 목록에서
  정확한 이름을 확인해서 VOICE_OPTIONS만 고치면 된다 - 나머지 코드는 그대로 재사용 가능.
- 2026-08-06 추가: 목소리가 2종류(자연스러움 등급)뿐이라 적다는 피드백으로 Chirp3 HD 등급을
  추가했다 - Neural2보다 최신이고 감정 표현/억양이 풍부한 등급(Google 공식 문서 기준 GA
  8종 중 4개 선택: Kore/Leda/Charon/Orus - 각각 "중립적이고 안정적", "차분하고 대화체",
  "묵직하고 신뢰감", "따뜻한 이야기체" 성격으로 소개됨, 면접 질문 낭독에 어울리는 톤 위주로
  골랐다. Puck/Fenrir처럼 밝고 들뜬 톤은 면접 맥락과 안 맞아 보여서 제외). 이름 규칙은
  `<locale>-Chirp3-HD-<보이스이름>` (예: ko-KR-Chirp3-HD-Kore). 요금은 Neural2와 동일하게
  월 100만자까지 무료, 초과 시 100만자당 $30(Neural2는 $16)로 좀 더 비싸다.
"""

from dataclasses import dataclass

import requests

from app.core.config import settings

_SYNTHESIZE_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
_TIMEOUT_SEC = 15


@dataclass
class VoiceOption:
    id: str
    label: str
    google_voice_name: str
    gender: str  # "FEMALE" | "MALE"

    def to_dict(self) -> dict:
        return {"id": self.id, "label": self.label, "gender": self.gender}


# 2026-08-07: 8개(Standard 2 + Neural2 2 + Chirp3 HD 4)까지 늘렸었는데, 선택 화면에서 칩이
# 3줄로 넘치고 "목소리가 너무 많다"는 피드백을 받았다. Standard(가장 기계음성 느낌 강함)와
# Neural2(1번/3번이 비슷하게 들린다는 피드백의 원인이었던 등급)를 빼고, 애초에 "더 자연스럽고
# 감정 표현 풍부한 목소리"를 원해서 추가했던 Chirp3 HD 4개(여성 2 + 남성 2)만 남겼다 - 품질도
# 가장 좋고 성별도 균형 잡혀 있어서 4개로 줄여도 선택지가 부족하지 않다.
# 2026-08-07 추가: label에 "(하이엔드)"를 붙였던 건 Standard/Neural2와 구분하기 위해서였는데,
# 이제 4개 다 Chirp3 HD뿐이라 구분할 대상이 없어졌다 - 그런데도 계속 붙어 있어서 칩 안에서
# "하이엔 드"처럼 단어가 잘리고 글씨가 눈에 안 들어온다는 피드백을 받았다. 의미 없어진 접미사라
# 그냥 뺐다.
VOICE_OPTIONS: list[VoiceOption] = [
    VoiceOption(id="ko-kore-hd", label="안정적인 여성", google_voice_name="ko-KR-Chirp3-HD-Kore", gender="FEMALE"),
    VoiceOption(id="ko-leda-hd", label="다정한 여성", google_voice_name="ko-KR-Chirp3-HD-Leda", gender="FEMALE"),
    VoiceOption(id="ko-charon-hd", label="묵직한 남성", google_voice_name="ko-KR-Chirp3-HD-Charon", gender="MALE"),
    VoiceOption(id="ko-orus-hd", label="편안한 남성", google_voice_name="ko-KR-Chirp3-HD-Orus", gender="MALE"),
]
DEFAULT_VOICE_ID = VOICE_OPTIONS[0].id

_VOICE_BY_ID = {v.id: v for v in VOICE_OPTIONS}


def list_voice_options() -> list[VoiceOption]:
    return VOICE_OPTIONS


def synthesize_speech(text: str, voice_id: str = DEFAULT_VOICE_ID) -> bytes:
    """text를 mp3 오디오 바이트로 변환해서 반환한다.

    voice_id가 VOICE_OPTIONS에 없는 값이면 기본 음성으로 대체한다(프론트가 구버전 캐시된
    voice_id를 보낼 수 있는 경우 대비 - 에러로 답변 낭독 자체가 막히는 것보다 낫다).
    """
    if not settings.google_tts_api_key:
        raise RuntimeError("GOOGLE_TTS_API_KEY가 설정되어 있지 않습니다.")
    if not text.strip():
        raise ValueError("낭독할 텍스트가 비어 있습니다.")

    voice = _VOICE_BY_ID.get(voice_id, VOICE_OPTIONS[0])

    response = requests.post(
        _SYNTHESIZE_URL,
        params={"key": settings.google_tts_api_key},
        json={
            "input": {"text": text},
            "voice": {"languageCode": "ko-KR", "name": voice.google_voice_name},
            "audioConfig": {"audioEncoding": "MP3", "speakingRate": 1.0},
        },
        timeout=_TIMEOUT_SEC,
    )
    response.raise_for_status()

    import base64

    audio_content_b64 = response.json().get("audioContent", "")
    if not audio_content_b64:
        raise RuntimeError("Google TTS 응답에 audioContent가 없습니다.")
    return base64.b64decode(audio_content_b64)
