"""사이트 전역 챗봇(assistant)이 "페이지 이동" 의도를 감지할 때 고를 수 있는 후보 경로 목록.

2026-08-10: 기존에 우측 하단 플로팅 아이콘을 누르면 바로 뜨던 "모의면접 연습 채팅"을 사이트
전체를 도와주는 범용 챗봇으로 바꾸면서(InterviewChatWidget -> SiteAssistantWidget) 새로
추가한 도메인. 모의면접 연습 자체는 이제 /mock-interview 페이지에서만 시작한다.

frontend/src/shared/constants/navigation.ts의 navigationItems와 반드시 같은 경로 집합을
유지해야 한다 - 여기 없는 경로로 이동시키면 404가 뜨고, 실제 네비게이션에 없는데 여기만
추가하면 챗봇이 존재하지 않는 척하는 페이지로 안내하게 된다. 한쪽만 바뀌면 반드시 같이
고쳐야 한다(question_generator.py의 QUESTION_CATEGORIES와 프론트 CATEGORY_OPTIONS가 같은
이유로 짝을 맞춰야 하는 것과 동일한 원칙).
"""

# (경로, 사람이 읽는 이름, Gemini가 이동 의도를 판단할 때 참고할 짧은 설명)
SITE_PAGES: tuple[tuple[str, str, str], ...] = (
    ("/", "대시보드", "로그인 후 첫 화면 - 요약 정보 모아보기"),
    ("/statistics", "ICT 관련 통계", "IT/개발 직군 채용시장 통계 대시보드"),
    ("/job-postings", "전체 채용공고", "등록된 채용공고 전체 목록/검색"),
    ("/locationjobs", "우리 동네 채용공고", "위치 기반으로 가까운 채용공고 찾기"),
    ("/jobs", "맞춤 채용공고", "회원 프로필 기반으로 추천된 채용공고"),
    ("/opportunities", "성장 기회 추천", "직무 역량을 키울 수 있는 대외활동/교육 등 추천"),
    ("/planner", "나의 플래너", "취업 준비 일정/할 일 관리"),
    ("/profile", "역량 프로필", "목표 직무, 기술 요약 등 커리어 프로필 입력/수정"),
    ("/resume", "이력서 작성 도우미", "자기소개서/프로젝트 경험을 질문식으로 작성하거나 첨삭받기"),
    ("/mock-interview", "AI 모의면접", "카메라·마이크 또는 채팅으로 모의면접 연습하기"),
    ("/timeline", "개인 타임라인", "지금까지의 모의면접 결과를 점수 추이 그래프와 지난 리포트로 다시 보기"),
    ("/question", "진로검사·글쓰기 도구", "커리어넷 진로심리검사 4종과 자기소개서용 맞춤법 검사기"),
    ("/account", "마이페이지", "회원 정보, 계정 설정"),
    ("/skill-relation", "채용공고 워드클라우드", "채용공고에서 자주 나오는 기술/역량 키워드 시각화"),
)

_KNOWN_PATHS = frozenset(path for path, _, _ in SITE_PAGES)


def is_known_path(path: str | None) -> bool:
    return path in _KNOWN_PATHS


def site_pages_prompt_block() -> str:
    return "\n".join(f"- {path} : {name} ({desc})" for path, name, desc in SITE_PAGES)
