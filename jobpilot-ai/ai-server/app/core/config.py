from pathlib import Path

from pydantic_settings import BaseSettings

# 2026-08-04: env_file="​.env"(상대경로)였는데, 이게 파이썬 실행 시점의 작업 디렉터리(cwd) 기준으로
# 해석돼서 - 예: ai-server/ml 아래에서 스크립트를 돌리면 - .env를 못 찾고 조용히 빈 값으로
# 넘어가는 문제가 있었다(예외도 안 남). GEMINI_API_KEY를 .env에 넣었는데도 settings.gemini_api_key가
# 계속 빈 문자열이라 Gemini 검수(question_generator.py _gemini_polish)가 항상 fail-open으로
# 원문을 그대로 통과시키고 있었던 원인이 이거였다.
# 이 파일(app/core/config.py) 위치 기준 절대경로로 고정해서 cwd와 무관하게 항상 ai-server/.env를 찾게 함.
_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"


class Settings(BaseSettings):
    database_url: str = "mysql+pymysql://jobpilot:jobpilot@localhost:3306/job"
    backend_base_url: str = "http://localhost:9000"
    # 백엔드 POST /api/v1/job-postings/ingest 호출 인증용 (InternalApiKeyFilter 참고).
    # 루트 .env의 INTERNAL_API_KEY와 같은 값이어야 한다.
    internal_api_key: str = ""

    # 모의면접 질문생성(question_generator.py) 결과 검수용. 없어도 기능은 동작한다 -
    # 검수를 그냥 건너뛰고 로컬 모델 결과를 그대로 쓴다 (fail-open). 백엔드 GitHubProjectAnalysis
    # 기능이 쓰는 것과 같은 키를 재사용해도 된다.
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.5-flash-lite"

    class Config:
        env_file = str(_ENV_FILE)


settings = Settings()
