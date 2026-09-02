"""site_map.SITE_PAGES가 프론트 라우터에 실제로 있는 경로만 담고 있는지 확인한다.

site_map.py 문서에 적힌 대로 두 목록은 손으로 맞춰야 하는데, 실제로 한쪽만 바뀐 적이
있었다(라우터에 없는 /question이 남아 있어서 챗봇이 404 페이지로 안내할 수 있었다).
자동 검증이 없으면 같은 사고가 반복되므로 여기서 잡는다.
"""

import re
from pathlib import Path

import pytest

from app.domain.assistant.site_map import SITE_PAGES, find_page_for_message

_ROUTER_PATH = Path(__file__).resolve().parents[2] / "frontend" / "src" / "app" / "router.tsx"


def _router_paths() -> set[str]:
    """router.tsx의 path 문자열을 모아 절대 경로 집합으로 만든다.

    라우터가 최상위 "/" 아래에 자식 라우트를 상대 경로("dashboard")로 두므로, 앞에
    슬래시가 없는 경로는 "/"를 붙여 절대 경로로 바꾼다. ":id" 같은 파라미터 라우트는
    챗봇이 제안할 수 있는 대상이 아니라 제외한다.
    """
    source = _ROUTER_PATH.read_text(encoding="utf-8")
    paths = set()
    for raw in re.findall(r'path:\s*"([^"]+)"', source):
        if ":" in raw:
            continue
        paths.add(raw if raw.startswith("/") else f"/{raw}")
    paths.add("/")  # 최상위 라우트의 index 자식(= 홈)
    return paths


@pytest.mark.skipif(not _ROUTER_PATH.exists(), reason="프론트 소스가 없는 배포 이미지에서는 검증 불가")
def test_every_site_page_exists_in_frontend_router():
    unknown = sorted(page.path for page in SITE_PAGES if page.path not in _router_paths())
    assert not unknown, f"라우터에 없는 경로가 챗봇 이동 후보에 있습니다: {unknown}"


def test_site_pages_have_preview_highlights():
    """미리보기 카드가 빈 칸으로 뜨지 않게, 모든 페이지에 볼거리 설명이 있어야 한다."""
    missing = [page.path for page in SITE_PAGES if not page.highlights]
    assert not missing, f"highlights가 비어 있는 페이지: {missing}"


def test_site_page_paths_are_unique():
    paths = [page.path for page in SITE_PAGES]
    assert len(paths) == len(set(paths))


@pytest.mark.parametrize(("message", "expected"), [
    ("이력서 쓰고 싶어", "/resume"),
    ("자소서 첨삭해줘", "/resume"),
    ("모의면접 어떻게 해?", "/mock-interview"),
    ("이용권 얼마야", "/account"),
    ("찜한 공고 어디서 봐", "/account"),
    ("채용공고 보고싶어", "/job-postings"),
    # 더 구체적인 표현("우리 동네" + "채용공고" 2건)이 "채용공고" 1건을 이겨야 한다.
    ("우리 동네 채용공고 있어?", "/locationjobs"),
    ("맞춤 공고 추천해줘", "/dashboard"),
    ("지난 면접 결과 보고싶어", "/timeline"),
    ("대외활동 뭐 있어?", "/opportunities"),
    ("플래너 열어줘", "/planner"),
])
def test_find_page_for_message_matches_expected_page(message, expected):
    page = find_page_for_message(message)
    assert page is not None and page.path == expected


@pytest.mark.parametrize("message", [
    "오늘 점심 뭐 먹지",
    "안녕",
    # "면접"만으로는 모의면접/타임라인 어느 쪽인지 알 수 없다 - 억지로 권하지 않는다.
    "면접 팁 알려줘",
    "",
    "   ",
])
def test_find_page_for_message_returns_none_without_a_clear_target(message):
    assert find_page_for_message(message) is None
