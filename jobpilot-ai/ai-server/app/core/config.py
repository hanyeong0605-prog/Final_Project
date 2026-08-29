from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings

# 2026-08-04: env_file="​.env"(상대경로)였는데, 이게 파이썬 실행 시점의 작업 디렉터리(cwd) 기준으로
# 해석돼서 - 예: ai-server/ml 아래에서 스크립트를 돌리면 - .env를 못 찾고 조용히 빈 값으로
# 넘어가는 문제가 있었다(예외도 안 남). GEMINI_API_KEY를 .env에 넣었는데도 settings.gemini_api_key가
# 계속 빈 문자열이라 Gemini 검수(question_generator.py _gemini_polish)가 항상 fail-open으로
# 원문을 그대로 통과시키고 있었던 원인이 이거였다.
# 이 파일(app/core/config.py) 위치 기준 절대경로로 고정해서 cwd와 무관하게 항상 ai-server/.env를 찾게 함.
_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"


class Settings(BaseSettings):
    database_url: str = Field(
        default="mysql+pymysql://jobpilot:jobpilot@localhost:3306/job",
        validation_alias=AliasChoices("DATABASE_URL", "DB_URL"),
    )
    db_username: str = "root"
    db_password: str = ""
    backend_base_url: str = "http://localhost:9000"
    # 백엔드 POST /api/v1/job-postings/ingest 호출 인증용 (InternalApiKeyFilter 참고).
    # 루트 .env의 INTERNAL_API_KEY와 같은 값이어야 한다.
    internal_api_key: str = ""
    # Optional read-only artifact mount. No model => only sentiment returns unavailable.
    sentiment_model_dir: str = "/models/sentiment"

    # 모의면접 질문생성(question_generator.py) 결과 검수용. 없어도 기능은 동작한다 -
    # 검수를 그냥 건너뛰고 로컬 모델 결과를 그대로 쓴다 (fail-open).
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.5-flash-lite"

    # 2026-08-06: 모의면접 질문 낭독용 - 브라우저 기본 TTS(SpeechSynthesisUtterance)가
    # 기계음성 느낌이 강하다는 피드백으로 Google Cloud Text-to-Speech(Neural2 등 자연스러운
    # 음성)로 교체했다. 없으면 기능은 그대로 동작한다 - tts.py가 fail-open으로 프론트에
    # 503을 돌려주고, 프론트는 그걸 보고 브라우저 기본 TTS로 자동 폴백한다.
    google_tts_api_key: str = ""

    # 2026-08-07: 모의면접 답변 STT용 - 기존엔 openai-whisper를 서버에서 직접 돌렸는데,
    # EC2 프리티어에서 디스크/메모리 부담이 크고 whisper의 다국어 범용 모델(특히 "small")이
    # 한국어 전용으로 튜닝된 모델보다 정확도가 떨어질 가능성이 높아서 Google Cloud
    # Speech-to-Text(REST, API 키 인증)로 교체했다 - google_tts_api_key와 같은 방식
    # (서비스 계정 JSON 대신 API 키, 로컬 개발 환경 세팅이 훨씬 간단함). 없으면 기능은
    # 그대로 동작한다 - transcribe()가 fail-open으로 빈 텍스트 + low_confidence=True를
    # 반환한다(audio_analysis.py 참고). 보통 google_tts_api_key와 같은 GCP 프로젝트의
    # 같은 키를 재사용해도 되지만(Speech-to-Text API도 그 프로젝트에서 활성화했다는 전제),
    # 별도 키/프로젝트로 쿼터를 분리하고 싶으면 다른 값을 넣으면 된다.
    google_stt_api_key: str = ""

    # 2026-08-10: EC2 프리티어(디스크/메모리 제약)에는 LoRA 모델 파일 자체가 안 올라가 있어서
    # (app/domain/interview/model/은 .gitignore로 저장소에서 제외됨 - 크기 문제가 아니라
    # "직접 학습시킨 모델이 실서비스에서 실제로 동작한다"는 걸 보여줘야 해서, 지금까지처럼
    # Gemini/코퍼스 폴백만으로는 부족하다는 판단), Tailscale(사설 메쉬 VPN)로 연결된 로컬/학원
    # PC에서 이 ai-server 코드를 그대로 한 벌 더 띄워두고(그 PC엔 모델 파일이 있음), EC2가
    # 그 PC의 /internal/lora/generate-candidates 엔드포인트를 원격 호출해서 실제 LoRA 추론
    # 결과를 받아온다(question_generator.py _fetch_raw_candidates_remote 참고). 이 값이 비어
    # 있으면 기존처럼 로컬에 모델 파일이 있을 때만 직접 로드해서 추론한다(로컬 개발 환경은
    # 동작 변화 없음) - fail-open이 아니라 "설정 안 하면 옛날 방식 그대로"인 스위치.
    # 예: http://100.x.y.z:8000 (Tailscale IP, 포트는 로컬에서 uvicorn 띄운 포트)
    lora_server_url: str = ""
    # Tailscale은 사설 네트워크라 인터넷에 안 열려 있지만, 그래도 최소한의 방어로 공유
    # 비밀키를 헤더에 실어 보낸다(internal_api_key와 같은 패턴). EC2 쪽 lora_server_key와
    # 로컬 PC 쪽 lora_server_key가 같은 값이어야 한다.
    lora_server_key: str = ""

    class Config:
        env_file = str(_ENV_FILE)


settings = Settings()
