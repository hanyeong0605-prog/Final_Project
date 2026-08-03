"""크롤러(원티드 등)가 공통으로 쓰는 공고 데이터 구조.

원래 zighang_scraper.py 안에 있었는데, 직행 크롤러를 없애면서 원티드/백엔드 전송
쪽에서 계속 필요한 dataclass와 헬퍼만 이 파일로 옮겼다."""

from dataclasses import dataclass


def _as_dict(value) -> dict:
    """API 응답 필드가 dict 하나일 수도, list([{...}, {...}])일 수도 있다
    (예: 원티드 jobLocation류 필드가 다중 지점이면 배열로 오는 경우가 있었음).
    list면 그 안의 첫 dict 요소를, 그 외(None 등)면 빈 dict를 안전하게 돌려준다."""
    if isinstance(value, dict):
        return value
    if isinstance(value, list):
        for item in value:
            if isinstance(item, dict):
                return item
    return {}


@dataclass
class ScrapedJobPosting:
    external_id: str
    title: str
    company_name: str
    source_url: str
    career: str | None
    employment_type: str | None
    location: str | None
    deadline_raw: str | None
    is_rolling_deadline: bool
    origin_site: str | None
    job_category: str | None
    description: str | None
    source_updated_at: str | None = None
