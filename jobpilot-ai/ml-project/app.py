import base64
import io
import json
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, RLock, Thread
from typing import Any
from urllib.parse import parse_qs, urlparse
import uvicorn
import pymysql
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from kiwipiepy import Kiwi
from sklearn.feature_extraction.text import TfidfVectorizer
from wordcloud import WordCloud

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ENV_FILE = BASE_DIR.parent / ".env"
load_dotenv(PROJECT_ENV_FILE, override=False)

SEED_CACHE_FILE = BASE_DIR / "cache" / "wordcloud_cache.json"
RUNTIME_CACHE_FILE = Path(
    os.getenv("WORDCLOUD_RUNTIME_CACHE_FILE", str(BASE_DIR / "runtime-cache" / "wordcloud_cache.json"))
)
DEFAULT_LINUX_FONT = Path("/usr/share/fonts/truetype/nanum/NanumGothic.ttf")
WINDOWS_FONT_CANDIDATES = (
    Path("C:/Windows/Fonts/malgun.ttf"),
    Path("C:/Windows/Fonts/malgunbd.ttf"),
)
REQUIRED_GROUPS = {"all", "required", "preferred"}
STOPWORDS = {
    "채용", "우대", "경력", "신입", "가능자", "관련", "업무", "자격", "요건",
    "성남시", "분당구", "서울특별시", "강남구", "구로구", "판교", "위치",
}

LOGGER = logging.getLogger("jobpilot.wordcloud")
CACHE_LOCK = RLock()
WORDCLOUD_CACHE: dict[str, Any] = {}
CACHE_SOURCE = "uninitialized"
LAST_REFRESH_AT: str | None = None
LAST_REFRESH_ERROR: str | None = None


def resolve_font_path() -> str:
    configured_font = os.getenv("WORDCLOUD_FONT_PATH")
    candidates = [Path(configured_font)] if configured_font else []

    if os.name == "nt":
        candidates.extend(WINDOWS_FONT_CANDIDATES)
    candidates.append(DEFAULT_LINUX_FONT)

    for font_path in candidates:
        if font_path.is_file():
            return str(font_path)

    checked_paths = ", ".join(str(path) for path in candidates)
    raise RuntimeError(
        "A Korean font for word-cloud rendering was not found. "
        f"Checked: {checked_paths}. Set WORDCLOUD_FONT_PATH to a valid .ttf file."
    )


FONT_PATH = resolve_font_path()


def read_cache(cache_file: Path) -> dict[str, Any]:
    with cache_file.open("r", encoding="utf-8") as source:
        cache = json.load(source)

    missing_groups = REQUIRED_GROUPS.difference(cache)
    if missing_groups:
        raise RuntimeError(
            f"Word cloud cache is incomplete: {cache_file}. "
            f"Missing groups: {', '.join(sorted(missing_groups))}."
        )
    return cache


def load_existing_cache() -> tuple[dict[str, Any], str] | None:
    for cache_file, source in ((RUNTIME_CACHE_FILE, "runtime"), (SEED_CACHE_FILE, "seed")):
        if not cache_file.exists():
            continue
        try:
            return read_cache(cache_file), source
        except (OSError, json.JSONDecodeError, RuntimeError) as error:
            LOGGER.warning("Ignoring invalid %s word-cloud cache at %s: %s", source, cache_file, error)
    return None


def initialize_cache() -> None:
    global CACHE_SOURCE, WORDCLOUD_CACHE
    loaded_cache = load_existing_cache()
    if loaded_cache is None:
        LOGGER.warning(
            "No usable word-cloud cache was found. A background refresh will build one from job_requirements."
        )
        return

    cache, source = loaded_cache
    with CACHE_LOCK:
        WORDCLOUD_CACHE = cache
        CACHE_SOURCE = source
    LOGGER.info("Loaded %s word-cloud cache", source)


def mysql_connection_settings() -> dict[str, Any]:
    raw_url = os.getenv("WORDCLOUD_DB_URL") or os.getenv("DB_URL")
    if not raw_url:
        raise RuntimeError("DB_URL or WORDCLOUD_DB_URL is required to refresh the word-cloud cache.")

    normalized_url = raw_url.removeprefix("jdbc:")
    parsed = urlparse(normalized_url)
    if parsed.scheme not in {"mysql", "mariadb"} or not parsed.hostname:
        raise RuntimeError("DB_URL must be a MySQL URL such as jdbc:mysql://host:3306/jobpilot.")

    username = os.getenv("WORDCLOUD_DB_USERNAME") or os.getenv("DB_USERNAME")
    password = os.getenv("WORDCLOUD_DB_PASSWORD") or os.getenv("DB_PASSWORD")
    if not username or password is None:
        raise RuntimeError("DB_USERNAME and DB_PASSWORD are required to refresh the word-cloud cache.")

    settings: dict[str, Any] = {
        "host": parsed.hostname,
        "port": parsed.port or 3306,
        "user": username,
        "password": password,
        "database": parsed.path.lstrip("/") or "jobpilot",
        "charset": "utf8mb4",
        "cursorclass": pymysql.cursors.Cursor,
        "connect_timeout": 10,
        "read_timeout": 30,
        "write_timeout": 30,
    }
    options = parse_qs(parsed.query)
    uses_ssl = any(value.lower() == "true" for key in ("useSSL", "requireSSL") for value in options.get(key, []))
    if uses_ssl:
        settings["ssl"] = {"check_hostname": False}
    return settings


def fetch_job_requirements() -> list[tuple[str, str]]:
    connection = pymysql.connect(**mysql_connection_settings())
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT content, source_excerpt, importance FROM job_requirements")
            rows = cursor.fetchall()
    finally:
        connection.close()

    requirements: list[tuple[str, str]] = []
    for content, excerpt, importance in rows:
        text = f"{content or ''} {excerpt or ''}".strip()
        if text:
            requirements.append((text, (importance or "").lower()))
    return requirements


def extract_keywords(kiwi: Kiwi, text: str) -> str:
    return " ".join(
        token.form
        for token in kiwi.tokenize(text)
        if (token.tag.startswith("N") or token.tag in {"SL", "SH"})
        and token.form not in STOPWORDS
        and len(token.form) > 1
    )


def score_documents(kiwi: Kiwi, documents: list[str]) -> dict[str, float]:
    processed_documents = [extract_keywords(kiwi, document) for document in documents]
    processed_documents = [document for document in processed_documents if document]
    if not processed_documents:
        return {}

    vectorizer = TfidfVectorizer(max_features=50, lowercase=False)
    matrix = vectorizer.fit_transform(processed_documents)
    scores = matrix.sum(axis=0).A1
    return dict(zip(vectorizer.get_feature_names_out(), (float(score) for score in scores)))


def build_cache_from_database() -> dict[str, Any]:
    requirements = fetch_job_requirements()
    if not requirements:
        raise RuntimeError("job_requirements contains no text to analyze.")

    kiwi = Kiwi()
    documents_by_group = {
        "all": [text for text, _ in requirements],
        "required": [text for text, importance in requirements if importance == "required"],
        "preferred": [text for text, importance in requirements if importance == "preferred"],
    }
    cache: dict[str, Any] = {
        group: {
            "scores": score_documents(kiwi, documents),
            "total_records": len(documents),
        }
        for group, documents in documents_by_group.items()
    }
    cache["_meta"] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": "job_requirements",
    }
    return cache


def write_runtime_cache(cache: dict[str, Any]) -> None:
    RUNTIME_CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = RUNTIME_CACHE_FILE.with_suffix(".tmp")
    with temporary_file.open("w", encoding="utf-8") as target:
        json.dump(cache, target, ensure_ascii=False, indent=2)
    temporary_file.replace(RUNTIME_CACHE_FILE)


def refresh_cache() -> bool:
    global CACHE_SOURCE, LAST_REFRESH_AT, LAST_REFRESH_ERROR, WORDCLOUD_CACHE
    try:
        cache = build_cache_from_database()
        write_runtime_cache(cache)
        with CACHE_LOCK:
            WORDCLOUD_CACHE = cache
            CACHE_SOURCE = "database"
            LAST_REFRESH_AT = cache["_meta"]["generated_at"]
            LAST_REFRESH_ERROR = None
        LOGGER.info("Refreshed word-cloud cache from %s records", cache["all"]["total_records"])
        return True
    except Exception as error:  # Keep the last usable cache available when DB refresh fails.
        LOGGER.exception("Word-cloud cache refresh failed; retaining the previous cache")
        with CACHE_LOCK:
            LAST_REFRESH_ERROR = str(error)
        return False


def refresh_loop(stop_event: Event) -> None:
    refresh_on_start = os.getenv("WORDCLOUD_CACHE_REFRESH_ON_START", "true").lower() == "true"
    interval_minutes = max(15, int(os.getenv("WORDCLOUD_CACHE_REFRESH_INTERVAL_MINUTES", "360")))

    if refresh_on_start:
        refresh_cache()
    while not stop_event.wait(interval_minutes * 60):
        refresh_cache()


@asynccontextmanager
async def lifespan(_: FastAPI):
    initialize_cache()
    stop_event = Event()
    refresh_enabled = os.getenv("WORDCLOUD_CACHE_REFRESH_ENABLED", "true").lower() == "true"
    refresh_thread: Thread | None = None
    if refresh_enabled:
        refresh_thread = Thread(target=refresh_loop, args=(stop_event,), daemon=True, name="wordcloud-cache-refresh")
        refresh_thread.start()

    yield

    stop_event.set()
    if refresh_thread is not None:
        refresh_thread.join(timeout=5)


app = FastAPI(title="JobPilot Word Cloud Service", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])


@app.get("/health")
def health() -> dict[str, Any]:
    with CACHE_LOCK:
        return {
            "status": "ok" if WORDCLOUD_CACHE else "degraded",
            "cached_groups": sorted(REQUIRED_GROUPS.intersection(WORDCLOUD_CACHE)),
            "cache_source": CACHE_SOURCE,
            "last_refresh_at": LAST_REFRESH_AT,
            "last_refresh_error": LAST_REFRESH_ERROR,
        }


@app.get("/api/wordcloud")
def generate_wordcloud(importance: str = Query("all", pattern="^(all|required|preferred)$")) -> dict[str, Any]:
    with CACHE_LOCK:
        cache_data = WORDCLOUD_CACHE.get(importance, {})
        scores = cache_data.get("scores", {})
        total_records = cache_data.get("total_records", 0)

    if not scores:
        raise HTTPException(status_code=503, detail="Word-cloud cache is being refreshed. Please try again shortly.")

    word_cloud = WordCloud(
        font_path=FONT_PATH,
        width=600,
        height=600,
        background_color="white",
        max_font_size=150,
        min_font_size=6,
    ).generate_from_frequencies(scores)
    image_buffer = io.BytesIO()
    word_cloud.to_image().save(image_buffer, format="PNG")
    return {
        "importance": importance,
        "total_records": total_records,
        "image_data": f"data:image/png;base64,{base64.b64encode(image_buffer.getvalue()).decode('ascii')}",
    }
if __name__ == "__main__":
    is_reload = os.getenv("APP_ENV", "local").lower() != "production"
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=is_reload)