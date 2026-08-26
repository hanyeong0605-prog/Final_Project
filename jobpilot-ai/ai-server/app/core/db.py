"""Spring 백엔드와 같은 MySQL에 직접 붙는 공유 커넥션 헬퍼.

matching/service.py가 역량 매칭 학습 데이터를 이 방식으로 읽고 있었다(job_matches 조회) -
job_requirement_retrieval.py(모의면접 RAG)도 같은 DB를 읽어야 해서, 중복 정의 대신 여기로
빼서 공유한다. 새 DB나 새 커넥션 설정이 필요하지 않다 - DATABASE_URL/DB_URL 환경변수를
그대로 재사용한다(Spring의 jdbc: URL 형식까지 그대로 파싱).
"""

from urllib.parse import quote_plus, urlparse

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine

from app.core.config import settings

_engine: Engine | None = None


def _database_url() -> str:
    raw = settings.database_url
    if raw.startswith("jdbc:"):
        raw = raw[5:]
    parsed = urlparse(raw)
    if parsed.scheme.startswith("mysql"):
        user = quote_plus(settings.db_username or "root")
        password = quote_plus(settings.db_password or "")
        database = parsed.path.lstrip("/") or "jobpilot"
        host = parsed.hostname or "localhost"
        port = parsed.port or 3306
        return f"mysql+pymysql://{user}:{password}@{host}:{port}/{database}?charset=utf8mb4"
    return raw


def get_engine() -> Engine:
    """프로세스 안에서 엔진 하나만 만들어 재사용한다(매 요청마다 새로 만들지 않음)."""
    global _engine
    if _engine is None:
        _engine = create_engine(_database_url(), pool_pre_ping=True)
    return _engine
