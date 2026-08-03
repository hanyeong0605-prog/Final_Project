from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.domain.crawler.router import router as crawler_router
from app.domain.crawler.scheduler import start_scheduler
from app.domain.interview.router import router as interview_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 매일 06:00에 직행 크롤링 + 백엔드 저장을 자동으로 돌린다 (scheduler.py 참고).
    start_scheduler()
    yield


app = FastAPI(title="JobPilot AI - Crawler Server", lifespan=lifespan)

# 2026-08-03: 모의면접 페이지가 브라우저에서 이 서버(8001)로 직접 오디오를 올리기 때문에
# CORS를 열어둔다. 다른 API(크롤러 등)는 서버-서버 호출뿐이라 필요 없었는데, 이건
# 프론트(5173)에서 브라우저로 직접 fetch하는 유일한 경로라서 추가함.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(crawler_router, prefix="/crawler", tags=["crawler"])
app.include_router(interview_router, prefix="/interview", tags=["interview"])


@app.get("/health")
def health():
    return {"status": "ok"}
