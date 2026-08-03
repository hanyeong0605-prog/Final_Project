from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "mysql+pymysql://jobpilot:jobpilot@localhost:3306/job"
    backend_base_url: str = "http://localhost:9000"

    class Config:
        env_file = ".env"


settings = Settings()
