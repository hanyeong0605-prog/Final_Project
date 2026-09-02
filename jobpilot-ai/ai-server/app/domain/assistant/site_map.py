"""사이트 전역 챗봇(assistant)이 "페이지 이동" 의도를 감지할 때 고를 수 있는 후보 경로 목록.

2026-08-10: 기존에 우측 하단 플로팅 아이콘을 누르면 바로 뜨던 "모의면접 연습 채팅"을 사이트
전체를 도와주는 범용 챗봇으로 바꾸면서(InterviewChatWidget -> SiteAssistantWidget) 새로
추가한 도메인. 모의면접 연습 자체는 이제 /mock-interview 페이지에서만 시작한다.

frontend/src/app/router.tsx의 실제 라우트와 반드시 같은 경로 집합을 유지해야 한다 - 여기
없는 경로로 이동시키면 404가 뜨고, 실제 라우터에 없는데 여기만 추가하면 챗봇이 존재하지도
않는 페이지로 안내하게 된다. 한쪽만 바뀌면 반드시 같이 고쳐야 한다(question_generator.py의
QUESTION_CATEGORIES와 프론트 CATEGORY_OPTIONS가 같은 이유로 짝을 맞춰야 하는 것과 동일한 원칙).

2026-09-02 highlights 추가: 챗봇이 "이동할까요?"라고 묻기만 하면 사용자는 그 페이지에 뭐가
있는지 모르는 채로 예/아니오를 골라야 했다. 이제 각 페이지마다 "거기서 실제로 뭘 볼 수
있는지"를 몇 줄로 적어두고, 이 내용을 (1) Gemini 프롬프트에 넣어 답변에서 미리 요약하게
하고 (2) chat.py가 suggested_page로 프론트에 내려보내 이동 전 미리보기 카드로 띄운다.

2026-09-02 keywords 추가 (중요): 처음엔 이동 후보를 오로지 Gemini가 JSON에
suggested_navigate_to를 채워주는지에만 의존했다. 이게 기능 전체를 모델 응답 하나에 건
설계라 실제로 자주 깨졌다 - 모델이 그 칸을 비우면 미리보기 카드도 예/아니오 버튼도 안 뜨고,
정작 답변 문장은 "이동할까요?"라고 물어놓은 상태가 된다(사용자가 "네"라고 해도 아무 일도
안 일어난다). 그래서 find_page_for_message()로 사용자 메시지에서 직접 페이지를 찾는 결정적
경로를 따로 뒀다 - Gemini가 채워주면 그걸 쓰고, 안 채워주면 이쪽이 받아낸다. LLM이 어떻게
답하든 이동 제안은 항상 같은 규칙으로 뜬다.
"""

from dataclasses import dataclass, field


@dataclass(frozen=True)
class SitePage:
    path: str
    name: str
    # Gemini가 이동 의도를 판단할 때 참고할 짧은 설명
    description: str
    # 이동 전 미리보기 카드/답변에 그대로 쓰이는 "이 페이지에서 실제로 볼 수 있는 것"
    highlights: tuple[str, ...]
    # find_page_for_message()가 쓰는 검색어. 이 페이지를 찾을 때만 쓰는 표현이어야 한다 -
    # 여러 페이지에 걸치는 흔한 말("면접", "공고" 단독)은 오탐을 만드니 넣지 않는다.
    keywords: tuple[str, ...] = field(default=())

    def to_dict(self) -> dict:
        return {
            "path": self.path,
            "name": self.name,
            "description": self.description,
            "highlights": list(self.highlights),
        }


SITE_PAGES: tuple[SitePage, ...] = (
    SitePage("/", "홈", "로그인 후 첫 화면 - 사이트 기능 둘러보기", (
        "맞춤 채용 분석·역량 프로필·AI 모의면접 바로가기",
        "관심 있을 만한 채용공고 캐러셀과 채용시장 요약",
    ), ("홈으로", "메인 화면", "첫 화면", "처음으로")),
    SitePage("/dashboard", "맞춤 채용 분석", "회원 프로필 기반으로 분석된 추천 채용공고", (
        "내 준비도(적합도) 점수와 부족한 필수 요건",
        "바로 지원 가능 / 요건 보완 후 도전 / 관심 공고 탭",
    ), ("맞춤 공고", "맞춤 채용", "추천 공고", "추천 채용", "적합도", "준비도", "매칭 공고",
        "나에게 맞는", "내게 맞는", "지원 가능", "대시보드")),
    SitePage("/job-postings", "전체 채용공고", "등록된 채용공고 전체 목록/검색", (
        "직무·지역·경력 조건으로 채용공고 검색",
        "공고 상세에서 요구 역량과 마감일 확인",
    ), ("채용공고", "전체 공고", "공고 목록", "공고 검색", "채용 정보", "구인", "일자리", "채용 공고")),
    SitePage("/locationjobs", "우리 동네 채용공고", "위치 기반으로 가까운 채용공고 찾기", (
        "지도에서 내 위치 주변 채용공고 보기",
        "출퇴근 거리 기준으로 공고 추리기",
    ), ("우리 동네", "동네 채용", "근처 공고", "가까운 공고", "지도", "출퇴근", "위치 기반")),
    SitePage("/opportunities", "성장 기회 추천", "직무 역량을 키울 수 있는 대외활동/교육 등 추천", (
        "부트캠프·교육·공모전 등 성장 기회 목록",
        "관심 직무에 맞는 활동 상세 정보",
    ), ("성장 기회", "대외활동", "공모전", "부트캠프", "교육 프로그램", "인턴십")),
    SitePage("/capability", "역량 관리", "보유 역량 점검과 부족한 역량 보완 계획", (
        "직무별 요구 역량 대비 내 역량 비교",
        "보완이 필요한 역량과 추천 학습 방향",
    ), ("역량 관리", "부족한 역량", "역량 점검", "역량 비교", "역량 분석")),
    SitePage("/profile", "역량 프로필", "목표 직무, 기술 요약 등 커리어 프로필 입력/수정", (
        "목표 직무, 기술 스택, 학력·자격증 저장",
        "여기 저장한 스펙이 맞춤 공고 추천의 기준이 됨",
    ), ("프로필", "내 스펙", "스펙 입력", "스펙 저장", "목표 직무", "기술 스택", "희망 직무")),
    SitePage("/resume", "이력서 작성 도우미", "자기소개서/프로젝트 경험을 질문식으로 작성하거나 첨삭받기", (
        "질문에 답하면 자기소개서 문단으로 정리",
        "작성한 글 AI 첨삭과 프로젝트 경험 정리",
    ), ("이력서", "자기소개서", "자소서", "첨삭", "포트폴리오", "경력기술서")),
    SitePage("/mock-interview", "AI 모의면접", "카메라·마이크 또는 채팅으로 모의면접 연습하기", (
        "인성/역량/직무 면접 유형과 질문 개수(3·5·7개) 선택",
        "끝나면 질문별 점수·강점·개선점 리포트 제공",
    ), ("모의면접", "면접 연습", "실전면접", "면접 보기", "면접 시작", "면접 볼래", "면접 연습")),
    SitePage("/timeline", "개인 타임라인", "지난 모의면접 결과를 점수 추이와 리포트로 다시 보기", (
        "회차별 점수 추이 그래프",
        "지난 면접 리포트 다시 열어보기",
    ), ("타임라인", "면접 결과", "점수 추이", "지난 면접", "면접 기록", "리포트 다시")),
    SitePage("/planner", "나의 플래너", "취업 준비 일정/할 일 관리", (
        "지원 마감일과 준비 일정 캘린더",
        "할 일 체크리스트로 준비 상황 관리",
    ), ("플래너", "일정", "캘린더", "할 일", "마감일", "스케줄")),
    SitePage("/community", "커뮤니티", "다른 구직자들과 취업 준비 정보 나누기", (
        "면접 후기·취업 준비 정보 게시글",
        "직접 글을 쓰고 댓글로 질문하기",
    ), ("커뮤니티", "게시판", "게시글", "후기", "글 쓰", "댓글")),
    SitePage("/statistics", "ICT 관련 통계", "IT/개발 직군 채용시장 통계 대시보드", (
        "직무·지역별 채용 수요 추이",
        "요구 경력·기술 분포 통계",
    ), ("통계", "채용시장", "시장 동향", "트렌드", "채용 수요")),
    SitePage("/skill-relation", "채용공고 워드클라우드", "채용공고에서 자주 나오는 기술/역량 키워드 시각화", (
        "공고에 자주 등장하는 기술 키워드 워드클라우드",
        "키워드끼리 함께 등장하는 관계 확인",
    ), ("워드클라우드", "키워드 시각", "기술 키워드", "많이 나오는 기술")),
    SitePage("/account", "마이페이지", "회원 정보, 계정 설정, 이용권", (
        "회원 정보·비밀번호 등 계정 설정",
        "실전면접 이용권 잔여 횟수와 결제 내역",
    ), ("마이페이지", "내 계정", "계정 설정", "이용권", "결제", "요금", "구독", "닉네임 변경",
        "비밀번호 변경", "회원 탈퇴", "찜 목록", "찜한")),
)

_PAGES_BY_PATH = {page.path: page for page in SITE_PAGES}


def is_known_path(path: str | None) -> bool:
    return path in _PAGES_BY_PATH


def find_page(path: str | None) -> SitePage | None:
    """검증된 경로에 해당하는 페이지 정보. 목록에 없는 경로면 None(프론트에 미리보기를
    내려보내기 전에 is_known_path()와 같은 기준으로 한 번 더 걸러진다)."""
    if path is None:
        return None
    return _PAGES_BY_PATH.get(path)


# "그 페이지로 가고 싶다"는 뜻이 실제로 드러난 표현. 주제어만으로 이동을 권하면 안 된다 -
# "이력서 쓰고 싶어"는 채팅에서 도와달라는 말이지 이력서 페이지로 보내달라는 말이 아니다.
_NAVIGATION_INTENT = (
    "이동", "가줘", "가주", "가고싶", "가고 싶", "가자", "갈래", "가볼", "가 볼", "들어가",
    "데려다", "열어", "열래", "보여줘", "보여 줘", "어디서", "어디로", "어디에", "어디야",
    "페이지", "화면", "바로가기",
)


def has_navigation_intent(message: str) -> bool:
    text = (message or "").strip().lower()
    return any(phrase in text for phrase in _NAVIGATION_INTENT)


def find_page_for_message(message: str) -> SitePage | None:
    """사용자가 "그 페이지로 가고 싶다"고 밝혔을 때만 이동 후보를 고른다(Gemini 호출 없음).

    Gemini가 suggested_navigate_to를 비워 보내는 경우를 받아내는 결정적 경로다 - 모델이
    어떻게 답하든 같은 요청에는 항상 같은 페이지가 제안되게 하려는 것이기도 하다.

    2026-09-02: 처음엔 주제어만 걸리면 바로 페이지를 제안했는데, 그러면 무슨 질문을 하든
    페이지 안내가 따라붙어서 정작 채팅 안에서 답을 보여주던 자리(적합도 높은 공고 카드 등)를
    덮어버렸다. 이제 이동 의사를 밝힌 표현이 함께 있어야만 후보를 고른다 - 답할 수 있는
    질문은 채팅에서 답하는 게 우선이다.

    가장 많은 keywords가 걸린 페이지를 고르고, 같은 개수면 더 긴(= 더 구체적인) 표현이
    걸린 쪽을 고른다. 하나도 안 걸리면 None - 억지로 아무 페이지나 권하지 않는다.
    """
    text = (message or "").strip().lower()
    if not text or not has_navigation_intent(text):
        return None

    best: tuple[int, int, SitePage] | None = None
    for page in SITE_PAGES:
        hits = [keyword for keyword in page.keywords if keyword.lower() in text]
        if not hits:
            continue
        score = (len(hits), max(len(keyword) for keyword in hits), page)
        if best is None or score[:2] > best[:2]:
            best = score
    return best[2] if best else None


def site_pages_prompt_block() -> str:
    lines = []
    for page in SITE_PAGES:
        lines.append(f"- {page.path} : {page.name} ({page.description})")
        for highlight in page.highlights:
            lines.append(f"    · {highlight}")
    return "\n".join(lines)
