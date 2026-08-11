"""크롤링 결과를 Spring 백엔드로 넘겨서 저장을 맡긴다.

DB 쓰기는 백엔드(JPA) 한 곳에서만 하도록 몰아둔 설계다 (ai-server는 SQLAlchemy로
직접 MySQL에 쓰지 않는다). 이렇게 하면 테이블 소유권과 마이그레이션 관리가 한쪽으로
통일된다. 백엔드의 POST /api/v1/job-postings/ingest 가 external_job_id 기준으로
upsert를 처리한다 (JobPostingIngestService 참고 - 2026-08-03 스키마 변경으로
job_sources/source_id 개념이 없어져서 external_job_id 하나로만 구분함). 같은
요청에서 마감 지난 공고를 CLOSED로 내리는 것까지 같이 하기 때문에, 새로 보낼 공고가
하나도 없어도(items=[]) 이 호출 자체는 항상 한다 - 안 그러면 마감 처리가 전혀 안 돈다.
"""

import requests

from app.core.config import settings
from app.domain.crawler.scraped_job_posting import ScrapedJobPosting

DEFAULT_TIMEOUT_SEC = 15


def _to_ingest_item(posting: ScrapedJobPosting) -> dict:
    """Python snake_case 필드를 백엔드 JobPostingCrawlItem(record)의 camelCase로 변환."""
    return {
        "externalId": posting.external_id,
        "title": posting.title,
        "companyName": posting.company_name,
        "sourceUrl": posting.source_url,
        "career": posting.career,
        "employmentType": posting.employment_type,
        "location": posting.location,
        "deadlineRaw": posting.deadline_raw,
        "isRollingDeadline": posting.is_rolling_deadline,
        "originSite": posting.origin_site,
        "jobCategory": posting.job_category,
        "description": posting.description,
        "sourceUpdatedAt": posting.source_updated_at,
        "imageUrls": posting.image_urls,
    }


def fetch_existing_source_updated_at(
    backend_base_url: str,
    source_code: str = "WANTED",
) -> dict[str, str]:
    """백엔드가 이미 갖고 있는 공고들의 {external_id: source_updated_at} 맵을 가져온다.

    크롤링 전에 한 번 호출해서 run_wanted_crawl(known_ids=...)에 넘기면, 이미 아는
    공고는 상세 페이지 요청 자체를 건너뛸 수 있다. 백엔드가 안 켜져 있는 등 실패하면
    빈 dict를 돌려줘서 "아무것도 모른다 -> 전부 새로 확인" 상태로 안전하게 폴백한다.
    sourceCode는 지금 스키마엔 없는 개념이라 백엔드에서 사용 안 하지만, 쿼리 파라미터
    시그니처만 유지한다.
    """
    try:
        resp = requests.get(
            f"{backend_base_url.rstrip('/')}/api/v1/job-postings/existing",
            params={"sourceCode": source_code},
            timeout=DEFAULT_TIMEOUT_SEC,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception:
        # 네트워크 오류든, 백엔드가 이상한 응답(JSON 아님 등)을 주든 이 함수는 절대
        # 크롤링 자체를 막으면 안 되는 사전 준비 단계라서 폭넓게 잡아 안전한 값으로 폴백.
        return {}


def start_crawl_run(backend_base_url: str, source_code: str, trigger_type: str) -> int | None:
    """Create a durable audit row before a crawl starts. Crawling must continue if audit logging is unavailable."""
    try:
        response = requests.post(
            f"{backend_base_url.rstrip('/')}/api/v1/job-postings/crawl-runs/start",
            json={"sourceCode": source_code, "triggerType": trigger_type},
            headers={"X-Internal-Api-Key": settings.internal_api_key},
            timeout=DEFAULT_TIMEOUT_SEC,
        )
        response.raise_for_status()
        return response.json().get("id")
    except Exception as error:
        print(f"[wanted] crawl run audit start failed: {error}")
        return None


def complete_crawl_run(backend_base_url: str, run_id: int | None, outcome: dict) -> None:
    if run_id is None:
        return
    try:
        response = requests.post(
            f"{backend_base_url.rstrip('/')}/api/v1/job-postings/crawl-runs/{run_id}/complete",
            json=outcome,
            headers={"X-Internal-Api-Key": settings.internal_api_key},
            timeout=DEFAULT_TIMEOUT_SEC,
        )
        response.raise_for_status()
    except Exception as error:
        print(f"[wanted] crawl run audit complete failed: {error}")


def send_to_backend(
    postings: list[ScrapedJobPosting],
    backend_base_url: str,
    source_code: str = "WANTED",
) -> dict:
    """items가 비어있어도 요청은 보낸다 - 백엔드가 이 요청에 얹어서 마감 지난
    공고를 CLOSED 처리하기 때문 (JobPostingIngestService.ingest() 참고)."""
    payload = {
        "sourceCode": source_code,
        "items": [_to_ingest_item(p) for p in postings],
    }
    resp = requests.post(
        f"{backend_base_url.rstrip('/')}/api/v1/job-postings/ingest",
        json=payload,
        headers={"X-Internal-Api-Key": settings.internal_api_key},
        timeout=DEFAULT_TIMEOUT_SEC,
    )
    resp.raise_for_status()
    return resp.json()
