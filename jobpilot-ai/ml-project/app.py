import base64
import io
import json
import os
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from wordcloud import WordCloud

BASE_DIR = Path(__file__).resolve().parent
CACHE_FILE = BASE_DIR / "cache" / "wordcloud_cache.json"
DEFAULT_LINUX_FONT = Path("/usr/share/fonts/truetype/nanum/NanumGothic.ttf")
WINDOWS_FONT_CANDIDATES = (
    Path("C:/Windows/Fonts/malgun.ttf"),
    Path("C:/Windows/Fonts/malgunbd.ttf"),
)

app = FastAPI(title="JobPilot Word Cloud Service")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])


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


def load_cache() -> dict:
    if not CACHE_FILE.exists():
        raise RuntimeError(
            f"Word cloud cache is missing: {CACHE_FILE}. "
            "This service uses the repository-managed precomputed cache; "
            "restore ml-project/cache/wordcloud_cache.json before starting the server."
        )

    try:
        with CACHE_FILE.open("r", encoding="utf-8") as cache_file:
            cache = json.load(cache_file)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"Word cloud cache is invalid JSON: {CACHE_FILE}") from error

    required_groups = {"all", "required", "preferred"}
    missing_groups = required_groups.difference(cache)
    if missing_groups:
        raise RuntimeError(
            f"Word cloud cache is incomplete: {CACHE_FILE}. "
            f"Missing groups: {', '.join(sorted(missing_groups))}."
        )
    return cache


FONT_PATH = resolve_font_path()
WORDCLOUD_CACHE = load_cache()


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "cached_groups": list(WORDCLOUD_CACHE.keys())}


@app.get("/api/wordcloud")
def generate_wordcloud(importance: str = Query("all", pattern="^(all|required|preferred)$")) -> dict:
    cache_data = WORDCLOUD_CACHE.get(importance, {"scores": {}, "total_records": 0})
    scores = cache_data.get("scores", {})
    if not scores:
        raise HTTPException(status_code=404, detail=f"No cached word cloud data for: {importance}")

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
        "total_records": cache_data.get("total_records", 0),
        "image_data": f"data:image/png;base64,{base64.b64encode(image_buffer.getvalue()).decode('ascii')}",
    }
