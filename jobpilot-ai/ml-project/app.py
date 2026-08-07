import base64
import io
import json
import os
import time
from typing import Optional
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from kiwipiepy import Kiwi
import pymysql
from sklearn.feature_extraction.text import TfidfVectorizer
from wordcloud import WordCloud

# .env 로드
load_dotenv()

app = FastAPI(title="Skill Trend ML Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 캐시 저장 경로 설정
CACHE_DIR = "cache"
CACHE_FILE = os.path.join(CACHE_DIR, "wordcloud_cache.json")

# 메모리 인메모리 캐시 변수
WORDCLOUD_CACHE = {}

kiwi = Kiwi()

STOPWORDS = {
    "채용", "우대", "경력", "신입", "가능자", "관련", "업무", "자격", "요건",
    "성남시", "분당구", "서울특별시", "강남구", "구로구", "판교", "위치"
}


# 1. DB에서 데이터 가져오기
def get_job_postings_from_db(importance: str = "all") -> list[str]:
    db_user = os.getenv("DB_USERNAME", "root")
    db_password = os.getenv("DB_PASSWORD")
    # DB_HOST, DB_NAME, DB_PORT는 .env에 없으면 기본값 사용
    db_host = os.getenv("DB_HOST", "localhost")
    db_port = int(os.getenv("DB_PORT", 3306))
    db_name = os.getenv("DB_NAME", "jobpilot")

    conn = pymysql.connect(
        host=db_host,
        port=db_port,
        user=db_user,
        password=db_password,
        db=db_name,
        charset="utf8mb4"
    )

    try:
        with conn.cursor() as cursor:
            sql = "SELECT content, source_excerpt, importance FROM job_requirements"
            cursor.execute(sql)
            rows = cursor.fetchall()

            results = []
            for row in rows:
                content = row[0] or ""
                excerpt = row[1] or ""
                imp = (row[2] or "all").lower()
                combined_text = f"{content} {excerpt}".strip()

                if combined_text:
                    results.append({"text": combined_text, "importance": imp})

            return results
    finally:
        conn.close()


def extract_keywords(text: str) -> str:
    """Kiwi 형태소 분석"""
    tokens = kiwi.tokenize(text)
    keywords = [
        token.form for token in tokens
        if token.tag.startswith("N") and token.form not in STOPWORDS and len(token.form) > 1
    ]
    return " ".join(keywords)


# 2. TF-IDF 연산 및 캐시 파일 생성 함수
def build_and_save_cache():
    """DB에서 데이터를 뽑아 TF-IDF 연산 후 cache/wordcloud_cache.json 저장"""
    global WORDCLOUD_CACHE
    print("DB에서 데이터셋을 조회하고 TF-IDF 학습을 진행합니다")

    raw_data = fetch_data_from_db()
    if not raw_data:
        print("DB에 학습할 데이터가 없습니다.")
        return

    os.makedirs(CACHE_DIR, exist_ok=True)
    new_cache = {}

    for target_imp in ["all", "required", "preferred"]:
        if target_imp == "all":
            docs = [item["text"] for item in raw_data]
        else:
            docs = [item["text"] for item in raw_data if item["importance"] == target_imp]

        if not docs:
            new_cache[target_imp] = {"scores": {}, "total_records": 0}
            continue

        # 전처리 및 TF-IDF 학습
        processed_docs = [extract_keywords(d) for d in docs if d.strip()]

        if processed_docs:
            vectorizer = TfidfVectorizer(max_features=50)
            tfidf_matrix = vectorizer.fit_transform(processed_docs)
            words = vectorizer.get_feature_names_out()
            scores = tfidf_matrix.sum(axis=0).A1
            word_scores = dict(zip(words, [float(s) for s in scores]))
        else:
            word_scores = {}

        new_cache[target_imp] = {
            "scores": word_scores,
            "total_records": len(docs)
        }

    # 파일 및 메모리에 저장
    with open(CACHE_FILE, "w", encoding="utf-8") as f:
        json.dump(new_cache, f, ensure_ascii=False, indent=2)

    WORDCLOUD_CACHE = new_cache
    print(f"데이터셋이 '{CACHE_FILE}'에 새로 저장되었습니다!")


# 3. 앱 시작 시 캐시 로드 (없으면 새로 생성)
@app.on_event("startup")
def init_cache():
    global WORDCLOUD_CACHE
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                WORDCLOUD_CACHE = json.load(f)
            print(f"기존 캐시 파일('{CACHE_FILE}')을 로드했습니다.")
        except Exception:
            build_and_save_cache()
    else:
        build_and_save_cache()


# 4. 워드클라우드 API
@app.get("/api/wordcloud")
def generate_wordcloud(importance: Optional[str] = Query("all")):
    start_time = time.time()
    imp_key = (importance or "all").lower()

    # 캐시된 데이터가 없으면 다시 캐시 생성
    if not WORDCLOUD_CACHE or imp_key not in WORDCLOUD_CACHE:
        build_and_save_cache()

    cache_data = WORDCLOUD_CACHE.get(imp_key, {"scores": {}, "total_records": 0})
    word_scores = cache_data["scores"]

    if not word_scores:
        raise HTTPException(status_code=404, detail=f"'{imp_key}' 조건의 분석 데이터가 없습니다.")

    # WordCloud 이미지 생성 (이미 학습된 가중치 수치 사용)
    wc = WordCloud(
        font_path="C:/Windows/Fonts/malgun.ttf",
        width=600,
        height=600,
        background_color="white",
        max_font_size = 150,
        min_font_size = 6

    ).generate_from_frequencies(word_scores)

    img_buffer = io.BytesIO()
    wc.to_image().save(img_buffer, format="PNG")
    base64_image = base64.b64encode(img_buffer.getvalue()).decode("utf-8")

    elapsed = round(time.time() - start_time, 2)


    return {
        "importance": imp_key,
        "total_records": cache_data["total_records"],
        "image_data": f"data:image/png;base64,{base64_image}"
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=8000)