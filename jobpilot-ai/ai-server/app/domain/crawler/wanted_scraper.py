"""
원티드(wanted.co.kr) 채용공고 스크래퍼.

정책/기술 메모 (2026-08-03 확인, PowerShell에서 직접 requests.get()으로 테스트함):
- 목록 API: GET /api/chaos/navigation/v1/results
  ?job_group_id=518&country=kr&job_sort=job.popularity_order&years=-1&locations=all
  &limit={n}&offset={n}
  job_group_id=518 = "개발"(IT개발·데이터) 카테고리. zighang과 달리 이미 IT로 필터된
  상태로 나오기 때문에 "일단 열어보고 아니면 버리는" 방식이 필요 없다 -> 훨씬 효율적.
- 상세 API: GET /api/chaos/jobs/v5/{id}/details
  intro/main_tasks/requirements/preferred_points/benefits/hire_rounds 등 본문 전체가
  이미 구조화된 JSON으로 온다 -> HTML 파싱이 필요 없다 (zighang의 JSON-LD보다도 더 풍부함).
- 둘 다 로그인 세션/쿠키 없이 User-Agent 헤더만 있으면 200으로 정상 응답한다
  (2026-08-03 PowerShell에서 실측 확인).
- 주의: robots.txt는 이 사이트에서 403(CloudFront 오류 페이지, "Request blocked")이
  나서 명확한 크롤링 허용 범위를 못 읽었고, 이용약관도 아직 확인 전이다. 지금은
  "일단 접근은 되는 것을 확인한" 단계이고, 실서비스 배포 전 반드시 재검토 필요.
  그래서 요청 간격(REQUEST_DELAY_SEC)을 두고, 실패 시 즉시 멈추도록 넉넉히 예외
  처리해뒀다 - 사이트에 부담을 최소화하는 방향으로 만듦.
- 목록 API 응답엔 zighang의 sitemap lastmod 같은 "수정시각" 필드가 없다. 그래서
  "안 바뀐 공고는 상세 요청 스킵" 같은 정교한 변경감지는 못 하고, 대신 이미 DB에
  있는 external_id는 상세 재요청 자체를 건너뛰는 단순한 방식으로 절약한다(known_ids).
  -> 즉 한 번 수집한 공고 내용이 이후에 바뀌어도 여기선 갱신되지 않는다는 한계가
  있음(추후 주기적 전체 재수집 등으로 보완 필요, 지금은 MVP).
- 상시채용(마감일 없음)이 많아서(due_time: null) 기존 백엔드의 "마감일 지나면
  CLOSED" 로직만으로는 원티드발 공고가 거의 안 닫힌다 - 이것도 추후 보완 필요한
  한계로 남겨둔다.
"""

import time

import requests

from app.domain.crawler.scraped_job_posting import ScrapedJobPosting, _as_dict

LIST_URL = "https://www.wanted.co.kr/api/chaos/navigation/v1/results"
DETAIL_URL_TMPL = "https://www.wanted.co.kr/api/chaos/jobs/v5/{id}/details"
DEFAULT_JOB_GROUP_ID = 518  # 개발(IT개발·데이터)

REQUEST_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    ),
    "Accept": "application/json",
}
REQUEST_DELAY_SEC = 0.7
LIST_PAGE_SIZE = 100
PROGRESS_LOG_EVERY = 5


MAX_RETRIES_429 = 3


def _fetch_json(url: str, params: dict | None = None, timeout: int = 15) -> dict:
    """429(Too Many Requests)면 지수 백오프로 재시도한다. 그 외 상태코드는 바로
    raise_for_status()로 올려서 run_wanted_crawl 쪽의 개별 항목 예외처리에 맡긴다."""
    for attempt in range(1, MAX_RETRIES_429 + 1):
        resp = requests.get(url, headers=REQUEST_HEADERS, params=params, timeout=timeout)
        if resp.status_code == 429:
            wait = REQUEST_DELAY_SEC * (2**attempt)
            print(f"[wanted] 429 Too Many Requests, {wait:.1f}초 대기 후 재시도 ({attempt}/{MAX_RETRIES_429}): {url}")
            time.sleep(wait)
            continue
        resp.raise_for_status()
        return resp.json()
    resp.raise_for_status()  # 재시도 다 써도 여전히 429면 여기서 HTTPError로 올림
    return resp.json()


def get_wanted_job_ids(
    job_group_id: int = DEFAULT_JOB_GROUP_ID,
    max_ids: int | None = None,
) -> list[int]:
    """목록 API를 offset 기반으로 끝까지(또는 max_ids까지) 페이지네이션하며 공고 id만 모은다.

    max_ids: 목록 수집 자체의 상한 (빠른 수동 테스트용, None이면 카테고리 전체를 다 모음)."""
    ids: list[int] = []
    offset = 0
    while True:
        if max_ids is not None and len(ids) >= max_ids:
            break
        params = {
            "job_group_id": job_group_id,
            "country": "kr",
            "job_sort": "job.popularity_order",
            "years": -1,
            "locations": "all",
            "limit": LIST_PAGE_SIZE,
            "offset": offset,
        }
        try:
            payload = _fetch_json(LIST_URL, params=params)
        except Exception as e:
            # 목록 페이지 하나가 실패해도 그때까지 모은 id는 살리고 멈춘다 - 상세 수집
            # 단계로 넘어가서 이미 모은 만큼이라도 처리할 수 있게 하기 위함.
            print(f"[wanted] 목록 요청 실패 (offset={offset}): {type(e).__name__}: {e}")
            break

        items = payload.get("data") or []
        if not items:
            break
        for item in items:
            job_id = item.get("id")
            if job_id is not None:
                ids.append(job_id)

        print(f"[wanted] 목록 offset {offset} 처리, 누적 id {len(ids)}개")
        if len(items) < LIST_PAGE_SIZE:
            break  # 마지막 페이지
        offset += LIST_PAGE_SIZE
        time.sleep(REQUEST_DELAY_SEC)

    return ids


def _career_text(annual_from, annual_to) -> str | None:
    if annual_from is None and annual_to is None:
        return None
    if annual_to is None or annual_to >= 100:
        return f"{annual_from}년 이상"
    return f"{annual_from}~{annual_to}년"


def _build_description(detail: dict) -> str | None:
    """intro/main_tasks/requirements/preferred_points/benefits/hire_rounds를
    zighang의 description 필드와 같은 "통짜 텍스트" 하나로 합친다."""
    sections = [
        ("소개", detail.get("intro")),
        ("주요업무", detail.get("main_tasks")),
        ("자격요건", detail.get("requirements")),
        ("우대사항", detail.get("preferred_points")),
        ("혜택 및 복지", detail.get("benefits")),
        ("채용 절차", detail.get("hire_rounds")),
    ]
    parts = [f"[{label}]\n{text}" for label, text in sections if text]
    return "\n\n".join(parts) if parts else None


def _image_urls(job: dict, detail: dict) -> list[str]:
    """원티드 상세 응답의 공고/회사 이미지 URL을 보존한다."""
    candidates = [
        _as_dict(job.get("images")).get("job_thumbnail_urls"),
        _as_dict(detail.get("images")).get("job_thumbnail_urls"),
    ]
    result: list[str] = []
    for urls in candidates:
        if not isinstance(urls, list):
            continue
        for url in urls:
            if isinstance(url, str) and url.startswith(("https://", "http://")) and url not in result:
                result.append(url)
    return result


def parse_wanted_job_detail(job_id: int) -> ScrapedJobPosting | None:
    payload = _fetch_json(DETAIL_URL_TMPL.format(id=job_id))

    data = payload.get("data") or {}
    job = data.get("job") or {}
    detail = job.get("detail") or {}
    if not detail.get("position"):
        # 비공개/삭제된 공고 등 - 조용히 건너뛴다.
        return None

    # 주의: company/address/category_tag/annual_from/annual_to/employment_type은
    # "data" 바로 밑이 아니라 "data.job" 안에 중첩돼 있다 (2026-08-03 실제 응답으로
    # 확인 - 처음엔 data.get(...)으로 잘못 짜서 title/description은 나오는데
    # 회사명/경력/고용형태/지역/카테고리가 전부 비어서 DB에 NULL로 저장되는 버그가 있었음).
    company = _as_dict(job.get("company"))
    address = _as_dict(job.get("address"))
    category = _as_dict(job.get("category_tag"))
    parent_tag = _as_dict(category.get("parent_tag"))

    location_parts = [p for p in [address.get("location"), address.get("district")] if p]
    location = " ".join(location_parts) if location_parts else None

    return ScrapedJobPosting(
        external_id=str(job_id),
        title=detail["position"],
        company_name=company.get("name") or "",
        source_url=f"https://www.wanted.co.kr/wd/{job_id}",
        career=_career_text(job.get("annual_from"), job.get("annual_to")),
        employment_type=job.get("employment_type"),
        location=location,
        deadline_raw=job.get("due_time"),
        is_rolling_deadline=job.get("due_time") is None,
        origin_site=None,  # 원티드 자체가 출처라 zighang 때와 달리 의미 없음.
        job_category=parent_tag.get("text"),
        description=_build_description(detail),
        source_updated_at=None,  # 목록 API에 수정시각이 없어서 변경 감지엔 못 씀.
        image_urls=_image_urls(job, detail),
    )


def run_wanted_crawl(
    limit: int | None = None,
    job_group_id: int = DEFAULT_JOB_GROUP_ID,
    max_ids: int | None = None,
    known_ids: set[str] | None = None,
    on_batch=None,
    batch_size: int = 200,
) -> list[ScrapedJobPosting]:
    """
    limit: 모을 공고 개수 상한 (None이면 새로 발견된 id를 전부 수집).
    max_ids: 목록 페이지네이션 자체의 상한 (빠른 수동 테스트용).
    known_ids: 이미 DB에 있는 external_id 집합. 이미 있는 id는 상세 요청 자체를
               건너뛴다 (모듈 상단 설명대로 zighang 같은 lastmod 비교는 못 하고,
               "이미 본 공고는 다시 안 본다" 수준으로 단순화한 절약).
    on_batch/batch_size: zighang과 동일한 배치 저장 콜백 - 대량 수집 중간에 실패해도
               이미 모은 만큼은 저장하기 위함.
    """
    known_ids = known_ids or set()
    ids = get_wanted_job_ids(job_group_id=job_group_id, max_ids=max_ids)
    print(f"[wanted] 총 후보 id {len(ids)}개, 이미 아는 공고 {len(known_ids)}개")

    results: list[ScrapedJobPosting] = []
    pending_batch: list[ScrapedJobPosting] = []
    scanned = 0
    skipped_known = 0
    fetch_errors = 0

    for job_id in ids:
        if limit is not None and len(results) >= limit:
            break
        if str(job_id) in known_ids:
            skipped_known += 1
            continue

        scanned += 1
        if scanned % PROGRESS_LOG_EVERY == 0:
            print(
                f"[wanted] 진행 중: {scanned}건 상세 요청, 수집 {len(results)}건, "
                f"이미 아는 공고 스킵 {skipped_known}건, 요청 실패 {fetch_errors}건"
            )
        try:
            item = parse_wanted_job_detail(job_id)
        except Exception as e:
            # 상세 요청/파싱 중 뭐가 터지든(네트워크, 예상 밖 JSON 구조 등) 그 한 건만
            # 건너뛰고 계속 진행한다 - zighang 크롤러에서 겪은 것과 같은 이유.
            fetch_errors += 1
            if fetch_errors <= 5 or fetch_errors % 50 == 0:
                print(f"[wanted] 상세 요청 실패 ({fetch_errors}번째, {type(e).__name__}): {e}")
            continue
        time.sleep(REQUEST_DELAY_SEC)

        if item is None:
            continue
        results.append(item)

        if on_batch is not None:
            pending_batch.append(item)
            if len(pending_batch) >= batch_size:
                print(f"[wanted] {len(pending_batch)}건 배치 저장 시도 (누적 {len(results)}건)")
                try:
                    on_batch(pending_batch)
                except Exception:
                    pass
                pending_batch = []

    if on_batch is not None and pending_batch:
        try:
            on_batch(pending_batch)
        except Exception:
            pass

    print(
        f"[wanted] 크롤링 종료: 총 {len(results)}건 수집 "
        f"({scanned}건 상세 요청, 이미 아는 공고 스킵 {skipped_known}건, 요청 실패 {fetch_errors}건)"
    )
    return results
