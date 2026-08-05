from fastapi import APIRouter, BackgroundTasks, Header, HTTPException

from app.core.config import settings
from app.domain.crawler.backend_client import fetch_existing_source_updated_at, send_to_backend
from app.domain.crawler.wanted_scraper import run_wanted_crawl

router = APIRouter()

# 2026-08-04: 스프링이 "크롤링 시작해줘" 하고 호출할 때 크롤링이 끝날 때까지(수십 분)
# 그 요청을 붙잡고 있게 하면 커넥션이 끊기기 쉽다. 그래서 background=True로 호출하면
# 백그라운드에서 돌리고 바로 202로 응답만 준다. 진행 상황은 이 dict에 기록해두고
# GET /wanted/status로 언제든 조회할 수 있게 한다 (프로세스 재시작하면 초기화되는
# 메모리 상태라 DB에 남기는 건 아니다 - MVP 수준).
_last_run_status: dict = {"state": "idle"}


def _verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    """스프링(또는 다른 내부 호출자)만 이 트리거를 부를 수 있게 하는 최소한의 검증.
    백엔드의 InternalApiKeyFilter와 같은 개념, 같은 키(.env의 INTERNAL_API_KEY)를 쓴다."""
    if not settings.internal_api_key or x_internal_api_key != settings.internal_api_key:
        raise HTTPException(status_code=401, detail="internal api key가 없거나 올바르지 않습니다.")

# save=True일 때 이 개수만큼 모일 때마다 바로바로 백엔드에 나눠서 저장한다.
# 예전엔 전체 크롤링이 다 끝난 뒤 딱 한 번에 저장했어서, 대량(수천 건) 크롤링 중간에
# 어디선가 하나라도 실패하면 그동안 모은 걸 통째로 날렸다. 나눠 보내면 일부만 저장되고
# 나머지에서 실패해도 이미 보낸 만큼은 DB에 남는다. 수동 트리거는 사람이 DB 카운트로
# 진행 상황을 지켜보는 경우가 많아서 20으로 작게 잡아 자주 반영되게 한다 (새벽 자동
# 배치는 scheduler.py에서 따로 200으로 씀 - 그쪽은 사람이 안 지켜보니 배치를 크게 해도 됨).
SAVE_BATCH_SIZE = 20


def _run_crawl_and_ingest(
    limit: int | None,
    job_group_id: int,
    max_ids: int | None,
    save: bool,
) -> dict:
    """실제 크롤링+저장 로직. 동기 호출(기존 방식)과 백그라운드 호출 양쪽에서 이 함수를
    그대로 재사용한다 - 로직을 두 벌로 유지하지 않기 위해 분리했다."""
    known = (
        fetch_existing_source_updated_at(settings.backend_base_url, source_code="WANTED")
        if save
        else {}
    )
    known_ids = set(known.keys())

    ingest_totals = {"received": 0, "created": 0, "updated": 0, "skipped": 0}
    ingest_errors: list[str] = []

    def flush_batch(batch):
        if not save or not batch:
            return
        try:
            result = send_to_backend(batch, settings.backend_base_url, source_code="WANTED")
            for key in ingest_totals:
                ingest_totals[key] += result.get(key, 0)
        except Exception as e:
            ingest_errors.append(str(e))

    try:
        result = run_wanted_crawl(
            limit=limit,
            job_group_id=job_group_id,
            max_ids=max_ids,
            known_ids=known_ids,
            on_batch=flush_batch if save else None,
            batch_size=SAVE_BATCH_SIZE,
        )
    except Exception as e:
        return {
            "crawled": 0,
            "items": [],
            "crawl_error": str(e),
            "ingest": ingest_totals,
        }

    PREVIEW_LIMIT = 20
    response = {"crawled": len(result)}
    if not save:
        response["items"] = [item.__dict__ for item in result[:PREVIEW_LIMIT]]
        if len(result) > PREVIEW_LIMIT:
            response["items_truncated"] = True

    if save:
        response["ingest"] = ingest_totals
        if ingest_errors:
            response["ingest_errors"] = ingest_errors

    return response


def _run_crawl_in_background(limit, job_group_id, max_ids, save):
    _last_run_status.update({"state": "running", "crawled_so_far": 0})
    try:
        outcome = _run_crawl_and_ingest(limit, job_group_id, max_ids, save)
        _last_run_status.update({"state": "done", **outcome})
    except Exception as e:
        # _run_crawl_and_ingest 자체가 이미 대부분의 예외를 잡지만, 혹시 모를 진짜
        # 예상 밖 예외까지 여기서 한 번 더 막아서 상태가 "running"에 영원히 멈춰있지
        # 않게 한다.
        _last_run_status.update({"state": "error", "error": str(e)})


@router.post("/wanted/run", status_code=200)
def trigger_wanted_crawl(
    background_tasks: BackgroundTasks,
    limit: int | None = None,
    job_group_id: int = 518,
    max_ids: int | None = None,
    save: bool = True,
    background: bool = False,
    x_internal_api_key: str | None = Header(default=None),
):
    """원티드(IT개발·데이터, job_group_id=518) 트리거.

    목록 API가 이미 IT로 필터돼서 나오기 때문에 zighang과 달리 it_only 옵션이 없다.
    limit=None(기본값)이면 목록에서 발견한 새 공고를 전부 수집한다. max_ids는 목록
    자체를 몇 개까지만 모을지 제한하는 빠른 테스트용 옵션(None이면 카테고리 전체).

    background=True로 호출하면(스프링이 부르는 방식) 크롤링이 끝날 때까지 기다리지
    않고 즉시 {"state": "started"}로 응답하고, 실제 진행은 백그라운드에서 계속된다.
    진행 상황은 GET /wanted/status로 확인한다. background=False(기본값, 사람이 직접
    curl로 부르는 경우)면 예전과 동일하게 끝날 때까지 기다렸다가 결과를 바로 돌려준다.

    2026-08-03 기준: robots.txt/이용약관 미확인 상태에서 접근 가능한 것만 확인하고
    만든 버전이다. 운영 전 반드시 재검토할 것 (wanted_scraper.py 상단 메모 참고)."""
    if background:
        # background=True는 스프링이 자동으로 호출하는 경로라 사람이 직접 curl로
        # 테스트할 때(background=False, 기본값)보다 엄격하게 키 검증을 요구한다.
        _verify_internal_api_key(x_internal_api_key)
        if _last_run_status.get("state") == "running":
            raise HTTPException(status_code=409, detail="이미 크롤링이 진행 중입니다.")
        background_tasks.add_task(_run_crawl_in_background, limit, job_group_id, max_ids, save)
        return {"state": "started"}

    return _run_crawl_and_ingest(limit, job_group_id, max_ids, save)


@router.get("/wanted/status")
def wanted_crawl_status():
    """background=True로 시작한 크롤링의 진행 상황 조회. 프로세스 메모리에만 있어서
    ai-server를 재시작하면 초기화된다."""
    return _last_run_status
