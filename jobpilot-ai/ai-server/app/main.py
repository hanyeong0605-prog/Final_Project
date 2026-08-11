from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.domain.assistant.router import router as assistant_router
from app.domain.certificate.router import router as certificate_router
from app.domain.crawler.router import router as crawler_router
from app.domain.crawler.scheduler import start_scheduler
from app.domain.interview.router import router as interview_router
from app.domain.resume.router import router as resume_router
from app.domain.timeline.router import router as timeline_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 매일 06:00에 직행 크롤링 + 백엔드 저장을 자동으로 돌린다 (scheduler.py 참고).
    start_scheduler()
    # 2026-08-07: STT를 로컬 whisper 모델에서 Google Cloud Speech-to-Text(REST API 호출)로
    # 교체하면서 로컬에 미리 로드해둘 무거운 모델 자체가 없어졌다 - 그래서 이전에 여기 있던
    # whisper 프리워밍 코드(_prewarm_whisper_model)를 제거했다. audio_analysis.py 모듈
    # docstring의 2026-08-07 항목 참고.
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
app.include_router(resume_router, prefix="/resume", tags=["resume"])
app.include_router(assistant_router, prefix="/assistant", tags=["assistant"])
app.include_router(timeline_router, prefix="/timeline", tags=["timeline"])
app.include_router(certificate_router, prefix="/certificates", tags=["certificates"])


@app.get("/health")
def health():
    return {"status": "ok"}
