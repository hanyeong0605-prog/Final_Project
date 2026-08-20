import base64
import io
import json
import logging
import os
import re
from collections import Counter
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, RLock, Thread
from typing import Any, Optional
from urllib.parse import parse_qs, urlparse
from uuid import uuid4

import numpy as np
from PIL import Image
from scipy.ndimage import binary_fill_holes, label
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
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
ADMIN_PHOTOS_DIR = Path(os.getenv("FACE_REFERENCE_DIR", str(BASE_DIR / "admin_photos"))).resolve()
ADMIN_PHOTOS_DIR.mkdir(parents=True, exist_ok=True)
# The production container receives this tracked asset through a read-only bind mount.
# Keep the source-tree location as the default for local execution.
MASK_IMAGE_PATH = Path(os.getenv(
    "WORDCLOUD_MASK_IMAGE",
    "/assets/mascot-fullbody-wordcloud.png" if Path("/assets/mascot-fullbody-wordcloud.png").exists()
    else str(BASE_DIR.parent / "frontend" / "public" / "mascot" / "mascot-fullbody-wordcloud.png"),
))

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
FALLBACK_TECH_SKILLS = (
    "Java", "Kotlin", "Python", "JavaScript", "TypeScript", "React", "Vue", "Angular",
    "Next.js", "Node.js", "Spring", "Spring Boot", "JPA", "MySQL", "PostgreSQL", "MongoDB",
    "Redis", "AWS", "Docker", "Kubernetes", "Git", "GitHub", "Linux", "Figma", "Flutter",
    "Android", "iOS", "TensorFlow", "PyTorch", "OpenAI", "FastAPI", "Django",
)

LOGGER = logging.getLogger("jobpilot.ai")
CACHE_LOCK = RLock()
WORDCLOUD_CACHE: dict[str, Any] = {}
WORDCLOUD_IMAGE_CACHE: dict[str, str] = {}
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
        # The mascot PNG has a transparent background.  Its alpha matte is a
        # much more accurate silhouette than trying to infer a shape from its
        # white body or black outlines.
        alpha = np.array(Image.open(MASK_IMAGE_PATH).convert("RGBA").getchannel("A"))
        silhouette = alpha > 32

        # Ignore tiny detached anti-aliasing artifacts, while preserving the
        # connected ears, body, paws and tail of the actual mascot.
        components, component_count = label(silhouette)
        if component_count:
            component_sizes = np.bincount(components.ravel())
            component_sizes[0] = 0
            silhouette = components == component_sizes.argmax()
        filled = binary_fill_holes(silhouette)

        # 빈 캔버스를 제거해 단어가 정사각형 전체가 아니라 고양이 실루엣을 꽉 채우게 한다.
        y, x = np.where(filled)
        if not len(y) or not len(x):
            raise ValueError("고양이 마스크에서 실루엣을 찾지 못했습니다.")
        padding = 14
        y0, y1 = max(0, y.min() - padding), min(filled.shape[0], y.max() + padding + 1)
        x0, x1 = max(0, x.min() - padding), min(filled.shape[1], x.max() + padding + 1)
        # 0: 글자 채움, 255: 배경
        CAT_MASK_CACHE = np.where(filled[y0:y1, x0:x1], 0, 255).astype(np.uint8)
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
    global CACHE_SOURCE, WORDCLOUD_CACHE, WORDCLOUD_IMAGE_CACHE
    loaded = load_existing_cache()
    if loaded:
        cache, source = loaded
        with CACHE_LOCK:
            WORDCLOUD_CACHE = cache
            WORDCLOUD_IMAGE_CACHE = {
                group: image for group, image in cache.get("_images", {}).items()
                if group in REQUIRED_GROUPS and isinstance(image, str)
            }
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


def normalize_technical_term(value: str) -> str:
    return re.sub(r"[\s._\-/]", "", value.lower())


def fetch_technical_catalog() -> dict[str, str]:
    """Maps catalog aliases to a display-safe canonical technical skill name."""
    catalog = {normalize_technical_term(skill): skill for skill in FALLBACK_TECH_SKILLS}
    try:
        conn = pymysql.connect(**mysql_connection_settings())
        try:
            with conn.cursor() as cursor:
                cursor.execute("""
                    SELECT s.name, s.normalized_name, a.alias, a.normalized_alias
                    FROM skills s
                    LEFT JOIN skill_aliases a ON a.skill_id = s.id
                    WHERE s.catalog_status = 'CANONICAL'
                """)
                rows = cursor.fetchall()
        finally:
            conn.close()
    except Exception as error:
        # The fixed fallback keeps the dashboard usable while an older database schema is upgraded.
        LOGGER.warning("기술 카탈로그를 읽지 못해 기본 기술 사전을 사용합니다: %s", error)
        return catalog

    for name, normalized_name, alias, normalized_alias in rows:
        canonical = str(name).strip()
        for candidate in (name, normalized_name, alias, normalized_alias):
            if candidate:
                normalized = normalize_technical_term(str(candidate))
                if normalized:
                    catalog[normalized] = canonical
    return catalog


def extract_technical_skills(text: str, catalog: dict[str, str]) -> list[str]:
    """Select known technologies only; generic recruiting nouns never enter the trend ranking."""
    normalized_text = normalize_technical_term(text)
    matches: set[str] = set()
    matched_aliases: list[str] = []
    # Long aliases first keeps Spring Boot / React Native from being reduced to a shorter skill.
    for alias, canonical in sorted(catalog.items(), key=lambda item: len(item[0]), reverse=True):
        # Two-character English strings (for example 'go') cause too many false positives.
        if len(alias) < 3 and alias not in {"c++"}:
            continue
        # If "springboot" already matched, do not add its less-specific "spring" fragment too.
        if alias in normalized_text and not any(alias in matched for matched in matched_aliases):
            matches.add(canonical)
            matched_aliases.append(alias)
    return sorted(matches)


def score_documents(documents: list[str], catalog: dict[str, str]) -> tuple[dict[str, float], Counter[str]]:
    extracted = [extract_technical_skills(doc, catalog) for doc in documents if doc]
    processed = [" ".join(skills) for skills in extracted if skills]
    if not processed:
        return {}, Counter()
    vectorizer = TfidfVectorizer(max_features=150, lowercase=False)
    matrix = vectorizer.fit_transform(processed)
    scores = matrix.sum(axis=0).A1
    mentions = Counter(skill for skills in extracted for skill in set(skills))
    return dict(zip(vectorizer.get_feature_names_out(), (float(s) for s in scores))), mentions


def top_keywords(scores: dict[str, float], mentions: Counter[str], limit: int = 5) -> list[dict[str, Any]]:
    ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:limit]
    return [
        {"rank": rank, "keyword": keyword, "score": round(score, 3), "mention_count": mentions.get(keyword, 0)}
        for rank, (keyword, score) in enumerate(ranked, start=1)
    ]


def build_cache_from_database() -> dict[str, Any]:
    requirements = fetch_job_requirements()
    if not requirements:
        raise RuntimeError("job_requirements에 분석할 데이터가 없습니다.")

    catalog = fetch_technical_catalog()
    docs_by_group = {
        "all": [text for text, _ in requirements],
        "required": [text for text, imp in requirements if imp == "required"],
        "preferred": [text for text, imp in requirements if imp == "preferred"],
    }
    cache: dict[str, Any] = {}
    for group, docs in docs_by_group.items():
        scores, mentions = score_documents(docs, catalog)
        cache[group] = {
            "scores": scores,
            "total_records": len(docs),
            "top_keywords": top_keywords(scores, mentions),
        }
    cache["_meta"] = {"generated_at": datetime.now(timezone.utc).isoformat(), "source": "job_requirements"}
    return cache


def write_runtime_cache(cache: dict[str, Any]) -> None:
    RUNTIME_CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    temp = RUNTIME_CACHE_FILE.with_suffix(".tmp")
    with temp.open("w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)
    temp.replace(RUNTIME_CACHE_FILE)


# Fixed brand palette keeps the visualization legible and visually consistent with Job-A-Dream.
BRAND_WORD_COLORS = ("#4338ca", "#4f46e5", "#6366f1", "#6d5ce7", "#7c6ee6", "#3b82f6", "#2563eb")


def brand_color_func(word: str, **_: Any) -> str:
    return BRAND_WORD_COLORS[sum(ord(char) for char in word) % len(BRAND_WORD_COLORS)]


def render_wordcloud_image(scores: dict[str, float]) -> str:
    mask = load_cat_mask()
    cloud = WordCloud(
        font_path=FONT_PATH,
        background_color="white",
        mask=mask,
        max_words=260,
        max_font_size=205,
        min_font_size=7,
        margin=1,
        prefer_horizontal=0.92,
        relative_scaling=0.28,
        # A limited technical vocabulary should still fill the mascot rather
        # than leave its ears, paws and tail empty. The separate Top 5 panel
        # remains the source of truth for rankings.
        repeat=True,
        contour_width=0,
        color_func=brand_color_func,
        random_state=42,
    ).generate_from_frequencies(scores)
    image = cloud.to_image()
    # Keep responses crisp on high-density displays without sending an unnecessarily large 2048px PNG.
    image.thumbnail((1280, 1280), Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    image.save(buf, format="PNG", optimize=True)
    return f"data:image/png;base64,{base64.b64encode(buf.getvalue()).decode('ascii')}"


def pre_render_wordclouds(cache: dict[str, Any]) -> dict[str, str]:
    return {
        group: render_wordcloud_image(cache[group]["scores"])
        for group in REQUIRED_GROUPS
        if cache.get(group, {}).get("scores")
    }


def refresh_cache() -> bool:
    global CACHE_SOURCE, LAST_REFRESH_AT, LAST_REFRESH_ERROR, WORDCLOUD_CACHE, WORDCLOUD_IMAGE_CACHE
    try:
        cache = build_cache_from_database()
        images = pre_render_wordclouds(cache)
        cache["_images"] = images
        write_runtime_cache(cache)
        with CACHE_LOCK:
            WORDCLOUD_CACHE = cache
            WORDCLOUD_IMAGE_CACHE = images
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
    interval = max(15, int(os.getenv("WORDCLOUD_CACHE_REFRESH_INTERVAL_MINUTES", "180")))
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
            "rendered_groups": sorted(REQUIRED_GROUPS.intersection(WORDCLOUD_IMAGE_CACHE)),
            "cache_source": CACHE_SOURCE,
            "last_refresh_at": LAST_REFRESH_AT,
            "last_refresh_error": LAST_REFRESH_ERROR,
        }


@app.get("/api/wordcloud")
def generate_wordcloud(importance: str = Query("all", pattern="^(all|required|preferred)$")) -> dict[str, Any]:
    # Requests normally return a pre-rendered PNG. A cold cache renders once as a safe fallback.
    with CACHE_LOCK:
        scores = WORDCLOUD_CACHE.get(importance, {}).get("scores", {})
        total_records = WORDCLOUD_CACHE.get(importance, {}).get("total_records", 0)
        rankings = WORDCLOUD_CACHE.get(importance, {}).get("top_keywords", [])
        image_data = WORDCLOUD_IMAGE_CACHE.get(importance)

    if not scores:
        raise HTTPException(status_code=503, detail="워드클라우드 캐시 준비 중입니다.")
    if image_data is None:
        image_data = render_wordcloud_image(scores)
        with CACHE_LOCK:
            WORDCLOUD_IMAGE_CACHE[importance] = image_data
    return {
        "importance": importance,
        "total_records": total_records,
        "top_keywords": rankings,
        "image_data": image_data,
    }


# ==============================================================================
# 🔒 안면 인식 2차 인증 (DeepFace)
# ==============================================================================
class FaceVerifyRequest(BaseModel):
    admin_id: str
    image_base64: str


def save_base64_image(base64_str: str, target_path: Path) -> None:
    if "," in base64_str:
        base64_str = base64_str.split(",")[1]
    missing_padding = len(base64_str) % 4
    if missing_padding:
        base64_str += "=" * (4 - missing_padding)

    try:
        image_bytes = base64.b64decode(base64_str, validate=True)
        # Reject non-images before DeepFace touches the file. This also prevents
        # the face endpoint being used as an arbitrary-file upload endpoint.
        with Image.open(io.BytesIO(image_bytes)) as image:
            image.verify()
    except Exception as error:
        raise HTTPException(status_code=400, detail="유효한 카메라 이미지가 아닙니다.") from error
    with open(target_path, "wb") as f:
        f.write(image_bytes)


@app.post("/api/internal/admin/face/verify")
def verify_admin_face(req: FaceVerifyRequest, x_internal_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    expected_api_key = os.getenv("INTERNAL_API_KEY", "")
    if not expected_api_key or x_internal_api_key != expected_api_key:
        raise HTTPException(status_code=401, detail="내부 서비스 인증에 실패했습니다.")
    if DeepFace is None:
        raise HTTPException(status_code=503, detail="안면 인증 런타임이 배포되어 있지 않습니다.")

    target_id = req.admin_id.strip()
    if not target_id or not target_id.replace("-", "").replace("_", "").isalnum():
        raise HTTPException(status_code=400, detail="유효하지 않은 관리자 식별자입니다.")
    img_data = req.image_base64

    admin_photo_path = ADMIN_PHOTOS_DIR / f"{target_id}.jpg"
    if not admin_photo_path.exists():
        admin_photo_path = ADMIN_PHOTOS_DIR / f"{target_id}.png"
        if not admin_photo_path.exists():
            raise HTTPException(status_code=404, detail="등록된 관리자 얼굴 사진이 없습니다.")

    # A user can tap the capture button again while a prior request is still
    # running. Give each request its own file so cleanup never removes another
    # request's image mid-analysis.
    temp_webcam_path = BASE_DIR / "runtime-cache" / f"temp_{target_id}_{uuid4().hex}.jpg"
    temp_webcam_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        save_base64_image(img_data, temp_webcam_path)

        result = DeepFace.verify(
            img1_path=str(temp_webcam_path),
            img2_path=str(admin_photo_path),
            model_name="VGG-Face",
            detector_backend="retinaface",
            enforce_detection=True,
        )

        distance = result.get("distance", 1.0)
        similarity = round((1 - distance) * 100, 2)
        # DeepFace's model-specific threshold is calibrated by the library.
        # Do not replace it with an arbitrary similarity percentage.
        threshold = result.get("threshold")
        is_matched = bool(result.get("verified", False))

        return {
            "admin_id": target_id,
            "verified": bool(is_matched),
            "similarity": similarity,
            "threshold": threshold,
            "message": "인증 성공" if is_matched else f"일치율 미달 (현재: {similarity}%, 기준: {threshold}%)",
        }
    except ValueError as error:
        LOGGER.info("No face detected for %s: %s", target_id, error)
        raise HTTPException(status_code=422, detail="사진에서 얼굴을 찾지 못했습니다. 얼굴 전체가 밝게 나오도록 다시 촬영해 주세요.") from error
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
