import base64
import io
import json
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, RLock, Thread
from typing import Any, Optional
from urllib.parse import parse_qs, urlparse

import numpy as np
from PIL import Image
from scipy.ndimage import binary_fill_holes
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from kiwipiepy import Kiwi
from pydantic import BaseModel
import pymysql
from sklearn.feature_extraction.text import TfidfVectorizer
import uvicorn
from wordcloud import WordCloud

try:
    from deepface import DeepFace
except ImportError:
    DeepFace = None

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ENV_FILE = BASE_DIR.parent / ".env"
load_dotenv(PROJECT_ENV_FILE, override=False)

SEED_CACHE_FILE = BASE_DIR / "cache" / "wordcloud_cache.json"
RUNTIME_CACHE_FILE = Path(
    os.getenv("WORDCLOUD_RUNTIME_CACHE_FILE", str(BASE_DIR / "runtime-cache" / "wordcloud_cache.json"))
)
ADMIN_PHOTOS_DIR = BASE_DIR / "admin_photos"
ADMIN_PHOTOS_DIR.mkdir(parents=True, exist_ok=True)
MASK_IMAGE_PATH = BASE_DIR.parent / "frontend" / "public" / "mascot" / "mascot_nukki.png"

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

LOGGER = logging.getLogger("jobpilot.ai")
CACHE_LOCK = RLock()
WORDCLOUD_CACHE: dict[str, Any] = {}
CAT_MASK_CACHE: Optional[np.ndarray] = None
CACHE_SOURCE = "uninitialized"
LAST_REFRESH_AT: str | None = None
LAST_REFRESH_ERROR: str | None = None


def resolve_font_path() -> str:
    configured = os.getenv("WORDCLOUD_FONT_PATH")
    candidates = [Path(configured)] if configured else []
    if os.name == "nt":
        candidates.extend(WINDOWS_FONT_CANDIDATES)
    candidates.append(DEFAULT_LINUX_FONT)

    for p in candidates:
        if p.is_file():
            return str(p)
    raise RuntimeError("워드클라우드용 한글 폰트를 찾을 수 없습니다.")


FONT_PATH = resolve_font_path()


def load_cat_mask() -> np.ndarray | None:
    """마스크 이미지를 1회만 변환하여 메모리에 상주시킵니다."""
    global CAT_MASK_CACHE
    if CAT_MASK_CACHE is not None:
        return CAT_MASK_CACHE

    if not MASK_IMAGE_PATH.exists():
        LOGGER.warning("마스크 이미지를 찾을 수 없습니다: %s", MASK_IMAGE_PATH)
        return None

    try:
        img = Image.open(MASK_IMAGE_PATH).convert("L")
        np_img = np.array(img)

        # 검은 선(외곽선) 추출 후 구멍 채우기
        outline = np_img < 90
        filled = binary_fill_holes(outline)

        # 형태 손상 없는 고해상도 마스크 생성 (0: 글자 채움, 255: 배경)
        CAT_MASK_CACHE = np.where(filled, 0, 255).astype(np.uint8)
        return CAT_MASK_CACHE
    except Exception as e:
        LOGGER.error("마스크 로드 에러: %s", str(e))
        return None


# ==============================================================================
# 📊 워드클라우드 데이터 캐시 및 텍스트 마이닝 로직
# ==============================================================================
def read_cache(cache_file: Path) -> dict[str, Any]:
    with cache_file.open("r", encoding="utf-8") as source:
        cache = json.load(source)
    if REQUIRED_GROUPS.difference(cache):
        raise RuntimeError("캐시 파일 데이터가 완전하지 않습니다.")
    return cache


def load_existing_cache() -> tuple[dict[str, Any], str] | None:
    for cache_file, source in ((RUNTIME_CACHE_FILE, "runtime"), (SEED_CACHE_FILE, "seed")):
        if not cache_file.exists():
            continue
        try:
            return read_cache(cache_file), source
        except (OSError, json.JSONDecodeError, RuntimeError):
            continue
    return None


def initialize_cache() -> None:
    global CACHE_SOURCE, WORDCLOUD_CACHE
    loaded = load_existing_cache()
    if loaded:
        cache, source = loaded
        with CACHE_LOCK:
            WORDCLOUD_CACHE = cache
            CACHE_SOURCE = source


def mysql_connection_settings() -> dict[str, Any]:
    raw_url = os.getenv("WORDCLOUD_DB_URL") or os.getenv("DB_URL")
    if not raw_url:
        raise RuntimeError("DB_URL이 설정되지 않았습니다.")

    parsed = urlparse(raw_url.removeprefix("jdbc:"))
    settings: dict[str, Any] = {
        "host": parsed.hostname,
        "port": parsed.port or 3306,
        "user": os.getenv("WORDCLOUD_DB_USERNAME") or os.getenv("DB_USERNAME"),
        "password": os.getenv("WORDCLOUD_DB_PASSWORD") or os.getenv("DB_PASSWORD"),
        "database": parsed.path.lstrip("/") or "jobpilot",
        "charset": "utf8mb4",
        "cursorclass": pymysql.cursors.Cursor,
        "connect_timeout": 10,
        "read_timeout": 30,
        "write_timeout": 30,
    }
    options = parse_qs(parsed.query)
    if any(v.lower() == "true" for k in ("useSSL", "requireSSL") for v in options.get(k, [])):
        settings["ssl"] = {"check_hostname": False}
    return settings


def fetch_job_requirements() -> list[tuple[str, str]]:
    conn = pymysql.connect(**mysql_connection_settings())
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT content, source_excerpt, importance FROM job_requirements")
            rows = cursor.fetchall()
    finally:
        conn.close()

    return [
        (f"{content or ''} {excerpt or ''}".strip(), (importance or "").lower())
        for content, excerpt, importance in rows
        if f"{content or ''} {excerpt or ''}".strip()
    ]


def extract_keywords(kiwi: Kiwi, text: str) -> str:
    return " ".join(
        t.form for t in kiwi.tokenize(text)
        if (t.tag.startswith("N") or t.tag in {"SL", "SH"}) and t.form not in STOPWORDS and len(t.form) > 1
    )


def score_documents(kiwi: Kiwi, documents: list[str]) -> dict[str, float]:
    processed = [extract_keywords(kiwi, doc) for doc in documents if doc]
    if not processed:
        return {}
    vectorizer = TfidfVectorizer(max_features=150, lowercase=False)
    matrix = vectorizer.fit_transform(processed)
    scores = matrix.sum(axis=0).A1
    return dict(zip(vectorizer.get_feature_names_out(), (float(s) for s in scores)))


def build_cache_from_database() -> dict[str, Any]:
    requirements = fetch_job_requirements()
    if not requirements:
        raise RuntimeError("job_requirements에 분석할 데이터가 없습니다.")

    kiwi = Kiwi()
    docs_by_group = {
        "all": [text for text, _ in requirements],
        "required": [text for text, imp in requirements if imp == "required"],
        "preferred": [text for text, imp in requirements if imp == "preferred"],
    }
    cache = {
        group: {"scores": score_documents(kiwi, docs), "total_records": len(docs)}
        for group, docs in docs_by_group.items()
    }
    cache["_meta"] = {"generated_at": datetime.now(timezone.utc).isoformat(), "source": "job_requirements"}
    return cache


def write_runtime_cache(cache: dict[str, Any]) -> None:
    RUNTIME_CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    temp = RUNTIME_CACHE_FILE.with_suffix(".tmp")
    with temp.open("w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)
    temp.replace(RUNTIME_CACHE_FILE)


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
        return True
    except Exception as error:
        with CACHE_LOCK:
            LAST_REFRESH_ERROR = str(error)
        return False


def refresh_loop(stop_event: Event) -> None:
    if os.getenv("WORDCLOUD_CACHE_REFRESH_ON_START", "true").lower() == "true":
        refresh_cache()
    interval = max(15, int(os.getenv("WORDCLOUD_CACHE_REFRESH_INTERVAL_MINUTES", "360")))
    while not stop_event.wait(interval * 60):
        refresh_cache()


@asynccontextmanager
async def lifespan(_: FastAPI):
    initialize_cache()
    load_cat_mask()  # 서버 시작 시 마스크를 미리 로드
    stop_event = Event()
    refresh_thread = None
    if os.getenv("WORDCLOUD_CACHE_REFRESH_ENABLED", "true").lower() == "true":
        refresh_thread = Thread(target=refresh_loop, args=(stop_event,), daemon=True)
        refresh_thread.start()
    yield
    stop_event.set()
    if refresh_thread:
        refresh_thread.join(timeout=5)


# ==============================================================================
# 🚀 FastAPI 앱 초기화 및 라우팅
# ==============================================================================
app = FastAPI(title="JobPilot AI Service", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


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
    # 1. 미리 계산되어 메모리에 있는 점수 데이터를 즉시 가져옴
    with CACHE_LOCK:
        scores = WORDCLOUD_CACHE.get(importance, {}).get("scores", {})
        total_records = WORDCLOUD_CACHE.get(importance, {}).get("total_records", 0)

    if not scores:
        raise HTTPException(status_code=503, detail="워드클라우드 캐시 준비 중입니다.")

    mask = load_cat_mask()

    # 2. 캐시된 점수로 고양이 형태 워드클라우드 즉시 생성
    wc = WordCloud(
        font_path=FONT_PATH,
        background_color="white",
        mask=mask,
        # colormap="Blues",
        max_words=120,
        max_font_size=200,
        min_font_size=5,
        prefer_horizontal=0.85,
        relative_scaling=0.2,
        contour_width=1.5 if mask is not None else 0,
        contour_color="#93c5fd" if mask is not None else None,
    ).generate_from_frequencies(scores)

    buf = io.BytesIO()
    wc.to_image().save(buf, format="PNG")
    return {
        "importance": importance,
        "total_records": total_records,
        "image_data": f"data:image/png;base64,{base64.b64encode(buf.getvalue()).decode('ascii')}",
    }


# ==============================================================================
# 🔒 안면 인식 2차 인증 (DeepFace)
# ==============================================================================
class FaceVerifyRequest(BaseModel):
    admin_id: Optional[Any] = "local-dev"
    adminId: Optional[Any] = None
    image_base64: Optional[str] = None
    imageBase64: Optional[str] = None


def save_base64_image(base64_str: str, target_path: Path) -> None:
    if "," in base64_str:
        base64_str = base64_str.split(",")[1]
    missing_padding = len(base64_str) % 4
    if missing_padding:
        base64_str += "=" * (4 - missing_padding)

    with open(target_path, "wb") as f:
        f.write(base64.b64decode(base64_str))


@app.post("/api/admin/face/verify")
def verify_admin_face(req: FaceVerifyRequest) -> dict[str, Any]:
    if DeepFace is None:
        raise HTTPException(status_code=503, detail="안면 인증 런타임이 배포되어 있지 않습니다.")

    target_id = str(req.admin_id or req.adminId or "local-dev").strip()
    img_data = req.image_base64 or req.imageBase64

    if not img_data:
        raise HTTPException(status_code=400, detail="카메라 이미지 데이터가 누락되었습니다.")

    admin_photo_path = ADMIN_PHOTOS_DIR / f"{target_id}.jpg"
    if not admin_photo_path.exists():
        admin_photo_path = ADMIN_PHOTOS_DIR / f"{target_id}.png"
        if not admin_photo_path.exists():
            admin_photo_path = ADMIN_PHOTOS_DIR / "local-dev.jpg"
            if not admin_photo_path.exists():
                raise HTTPException(status_code=404, detail=f"등록된 관리자 사진({target_id})이 없습니다.")

    temp_webcam_path = BASE_DIR / "runtime-cache" / f"temp_{target_id}.jpg"
    temp_webcam_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        save_base64_image(img_data, temp_webcam_path)

        result = DeepFace.verify(
            img1_path=str(temp_webcam_path),
            img2_path=str(admin_photo_path),
            model_name="VGG-Face",
            enforce_detection=False,
        )

        distance = result.get("distance", 1.0)
        similarity = round((1 - distance) * 100, 2)
        threshold = 50.0
        is_matched = similarity >= threshold

        return {
            "admin_id": target_id,
            "verified": bool(is_matched),
            "similarity": similarity,
            "threshold": threshold,
            "message": "인증 성공" if is_matched else f"일치율 미달 (현재: {similarity}%, 기준: {threshold}%)",
        }
    except Exception as error:
        LOGGER.exception("Face verify error for %s", target_id)
        raise HTTPException(status_code=500, detail=f"얼굴 분석 실패: {str(error)}")
    finally:
        if temp_webcam_path.exists():
            try:
                temp_webcam_path.unlink()
            except OSError:
                pass


if __name__ == "__main__":
    is_reload = os.getenv("APP_ENV", "local").lower() != "production"
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=is_reload)