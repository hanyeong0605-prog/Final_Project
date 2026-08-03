"""주기 실행용 스케줄러. app/main.py의 lifespan에서 앱 시작 시 자동 등록된다.

2026-08-03: 직행(zighang) 크롤러는 제거하고 원티드만 쓰기로 해서, 매일 자동
수집도 원티드 하나만 등록한다."""

from apscheduler.schedulers.background import BackgroundScheduler

from app.core.config import settings
from app.domain.crawler.backend_client import fetch_existing_source_updated_at, send_to_backend
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
    known = fetch_existing_source_updated_at(settings.backend_base_url, source_code="WANTED")
    known_ids = set(known.keys())

    totals = {"received": 0, "created": 0, "updated": 0, "skipped": 0}

    def flush_batch(batch):
        if not batch:
            return
        result = send_to_backend(batch, settings.backend_base_url, source_code="WANTED")
        for key in totals:
            totals[key] += result.get(key, 0)

    run_wanted_crawl(
        limit=DAILY_WANTED_MAX_NEW,
        known_ids=known_ids,
        on_batch=flush_batch,
        batch_size=DAILY_WANTED_SAVE_BATCH_SIZE,
    )
    return totals


def start_scheduler() -> BackgroundScheduler:
    scheduler = BackgroundScheduler(timezone="Asia/Seoul")
    scheduler.add_job(run_daily_wanted_crawl_and_save, "cron", hour=6, minute=0)  # 원티드 매일 06:00 실행
    scheduler.start()
    return scheduler
