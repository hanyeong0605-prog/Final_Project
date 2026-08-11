"""주기 실행용 스케줄러. app/main.py의 lifespan에서 앱 시작 시 자동 등록된다.

2026-08-03: 직행(zighang) 크롤러는 제거하고 원티드만 쓰기로 해서, 매일 자동
수집도 원티드 하나만 등록한다."""

from apscheduler.schedulers.background import BackgroundScheduler

from app.core.config import settings
from app.domain.crawler.backend_client import (
    complete_crawl_run,
    fetch_existing_source_updated_at,
    send_to_backend,
    start_crawl_run,
)
from app.domain.crawler.wanted_scraper import run_wanted_crawl

# 원티드는 목록 API가 이미 IT로 필터돼서 나오기 때문에 상세 요청 상한을 넉넉히 잡아도
# 부담이 적다. 다만 무한정 돌지 않게 그래도 상한은 둔다 - 카테고리 전체(2천여 건)를
# 하루 안에 다 훑을 수 있는 넉넉한 값.
DAILY_WANTED_MAX_NEW = 5000

# 수동 트리거(router.py)와 마찬가지로 다 모은 다음 한 번에 저장하지 않고 이 개수씩
# 나눠서 바로바로 저장한다 - 새벽 배치는 몇천 건을 몇 시간에 걸쳐 모으는데, 중간에
# 페이지 하나 문제로 실패해도(요즘은 어지간하면 안 죽지만) 이미 모은 만큼은 남긴다.
DAILY_WANTED_SAVE_BATCH_SIZE = 200


def run_daily_wanted_crawl_and_save() -> dict:
    run_id = start_crawl_run(settings.backend_base_url, source_code="WANTED", trigger_type="SCHEDULED")
    known = fetch_existing_source_updated_at(settings.backend_base_url, source_code="WANTED")
    known_ids = set(known.keys())

    totals = {
        "status": "COMPLETED", "candidateCount": 0, "detailRequests": 0, "skippedKnownCount": 0,
        "collectedCount": 0, "receivedCount": 0, "createdCount": 0, "updatedCount": 0,
        "skippedCount": 0, "failureCount": 0, "errorMessage": None,
    }

    def flush_batch(batch):
        if not batch:
            return
        result = send_to_backend(batch, settings.backend_base_url, source_code="WANTED")
        totals["receivedCount"] += result.get("received", 0)
        totals["createdCount"] += result.get("created", 0)
        totals["updatedCount"] += result.get("updated", 0)
        totals["skippedCount"] += result.get("skipped", 0)

    try:
        run_wanted_crawl(
            limit=DAILY_WANTED_MAX_NEW,
            known_ids=known_ids,
            on_batch=flush_batch,
            batch_size=DAILY_WANTED_SAVE_BATCH_SIZE,
            stats=totals,
        )
        # If every candidate is already known, still call ingest once so expired postings are closed.
        if totals["receivedCount"] == 0:
            result = send_to_backend([], settings.backend_base_url, source_code="WANTED")
            totals["receivedCount"] += result.get("received", 0)
            totals["createdCount"] += result.get("created", 0)
            totals["updatedCount"] += result.get("updated", 0)
            totals["skippedCount"] += result.get("skipped", 0)
    except Exception as error:
        totals["status"] = "FAILED"
        totals["errorMessage"] = str(error)[:4000]
        raise
    finally:
        complete_crawl_run(settings.backend_base_url, run_id, totals)
    return totals


def start_scheduler() -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="Asia/Seoul")
    scheduler.add_job(run_daily_wanted_crawl_and_save, "cron", hour=6, minute=0)  # 원티드 매일 06:00 실행
    scheduler.start()
    return scheduler
