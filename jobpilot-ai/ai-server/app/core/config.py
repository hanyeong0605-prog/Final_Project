from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "mysql+pymysql://jobpilot:jobpilot@localhost:3306/job"
    backend_base_url: str = "http://localhost:9000"
    # 백엔드 POST /api/v1/job-postings/ingest 호출 인증용 (InternalApiKeyFilter 참고).
    # 루트 .env의 INTERNAL_API_KEY와 같은 값이어야 한다.
    internal_api_key: str = ""

    class Config:
        env_file = ".env"


settings = Settings()
