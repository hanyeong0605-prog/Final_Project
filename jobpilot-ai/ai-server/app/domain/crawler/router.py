from fastapi import APIRouter

from app.core.config import settings
from app.domain.crawler.backend_client import fetch_existing_source_updated_at, send_to_backend
from app.domain.crawler.wanted_scraper import run_wanted_crawl

router = APIRouter()

# save=True일 때 이 개수만큼 모일 때마다 바로바로 백엔드에 나눠서 저장한다.
# 예전엔 전체 크롤링이 다 끝난 뒤 딱 한 번에 저장했어서, 대량(수천 건) 크롤링 중간에
# 어디선가 하나라도 실패하면 그동안 모은 걸 통째로 날렸다. 나눠 보내면 일부만 저장되고
# 나머지에서 실패해도 이미 보낸 만큼은 DB에 남는다. 수동 트리거는 사람이 DB 카운트로
# 진행 상황을 지켜보는 경우가 많아서 20으로 작게 잡아 자주 반영되게 한다 (새벽 자동
# 배치는 scheduler.py에서 따로 200으로 씀 - 그쪽은 사람이 안 지켜보니 배치를 크게 해도 됨).
SAVE_BATCH_SIZE = 20


@router.post("/wanted/run")
def trigger_wanted_crawl(
    limit: int | None = None,
    job_group_id: int = 518,
    max_ids: int | None = None,
    save: bool = True,
):
    """원티드(IT개발·데이터, job_group_id=518) 수동 트리거.

    목록 API가 이미 IT로 필터돼서 나오기 때문에 zighang과 달리 it_only 옵션이 없다.
    limit=None(기본값)이면 목록에서 발견한 새 공고를 전부 수집한다. max_ids는 목록
    자체를 몇 개까지만 모을지 제한하는 빠른 테스트용 옵션(None이면 카테고리 전체).

    2026-08-03 기준: robots.txt/이용약관 미확인 상태에서 접근 가능한 것만 확인하고
    만든 버전이다. 운영 전 반드시 재검토할 것 (wanted_scraper.py 상단 메모 참고)."""
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
