import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.domain.crawler.router import router as crawler_router
from app.domain.crawler.scheduler import start_scheduler
from app.domain.interview.router import router as interview_router


def _prewarm_whisper_model() -> None:
    """2026-08-07: audio_analysis._get_whisper_model()은 원래 첫 실제 요청 때 지연 로딩된다
    (모듈 docstring 참고 - 환경이 whisper를 못 돌리면 서버 자체는 뜨게 하려는 의도였음).
    근데 배포 환경(EC2)에서 실측해보니 이 첫 로딩이 답변 분석 요청 하나를 ~35초씩 더 늦춰서
    (컨테이너 켜지고 첫 면접 답변에서만) 사용자가 "다음 질문이 안 나온다"고 느낄 정도였다.
    그래서 서버 기동 시 한 번 미리 불러 둔다 - 실패해도 그냥 넘어가면 기존처럼 첫 실제
    요청 때 지연 로딩되니 fail-open이고, 로딩 자체가 안 되는 환경에서 서버가 죽는 일은 없다."""
    try:
        from app.domain.interview.audio_analysis import _get_whisper_model

        _get_whisper_model()
    except Exception:
        pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 매일 06:00에 직행 크롤링 + 백엔드 저장을 자동으로 돌린다 (scheduler.py 참고).
    start_scheduler()
    # 이벤트 루프를 막지 않도록 스레드로 돌린다 - health 체크나 다른 요청이 이 로딩 때문에
    # 밀리면 안 되므로(도커 healthcheck의 start_period/retries가 넉넉하긴 하지만 그와
    # 별개로 서버 자체는 즉시 응답 가능한 상태가 되는 게 맞다).
    asyncio.create_task(asyncio.to_thread(_prewarm_whisper_model))
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
