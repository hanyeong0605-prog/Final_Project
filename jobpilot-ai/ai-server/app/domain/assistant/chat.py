"""사이트 전역 챗봇 - 일반 대화 응답 + 페이지 이동 의도 감지 (Gemini + RAG).

2026-08-10: InterviewChatWidget(모의면접 전용 팝업)을 대체하는 범용 도우미. resume 도메인과
같은 fail-open 원칙 - Gemini 키가 없거나 호출이 실패하면 예외 없이 ok=False만 반환한다.

이 모듈은 대화 응답뿐 아니라 "이력서 페이지로 가줘" 같은 요청을 감지해서 navigate_to에
경로를 채워 돌려준다(요청 시 답변에서 확정한 기능 범위: "대화 + 페이지 이동"). 실제 라우팅은
프론트(SiteAssistantWidget)가 useNavigate()로 수행하고, 여기선 site_map.SITE_PAGES 안의
경로만 후보로 제시/검증한다 - Gemini가 목록에 없는 경로를 지어내 반환해도 is_known_path()로
한 번 더 걸러서 무조건 null로 떨어뜨린다(존재하지 않는 페이지로 안내하는 사고를 막기 위함).

2026-08-20 RAG 추가: 기존엔 사이트 페이지 목록(고정 15개) 말고는 전부 Gemini 자체 지식에
맡겼다 - "구독 요금이 얼마냐", "기업회원 승인 절차가 어떻게 되냐" 같은 이 사이트 고유의
정책/기능 질문엔 Gemini가 실제 답을 모르니 얼버무리거나 지어낼 위험이 있었다. 이제
knowledge.py가 사용자 메시지와 관련 있는 사이트 지식 조각(실제 코드/정책 기반 FAQ)을
로컬 TF-IDF로 검색해서 찾아주면, 관련 있는 것만 프롬프트에 "[사이트 지식 참고자료]"로
끼워넣는다 - 관련 지식이 없으면(예: 일반 잡담) 그 섹션 자체를 안 넣고 기존처럼 동작한다.

2026-09-02 이동 전 미리보기: 예전엔 "정보를 묻는 질문에는 페이지를 제안하지 말라"고 막아둬서,
사용자는 답만 받고 그 다음 행동(어디로 가야 하는지)은 스스로 찾아야 했다. 이제 답변은 그대로
완결되게 하되 이어서 볼 페이지가 있으면 suggested_navigate_to를 채우고, 그 페이지에서 볼 수
있는 것(site_map의 highlights)을 reply에서 미리 요약한 뒤 이동 여부를 묻는다. 같은 highlights를
suggested_page로 내려보내면 프론트가 이동 전 미리보기 카드로 띄운다.
"""

from dataclasses import dataclass

from app.core.config import settings
from app.domain.assistant import knowledge
from app.domain.assistant.job_match_retrieval import (
    JobMatchReference,
    fetch_active_matches,
    is_job_question,
    job_matches_prompt_block,
)
from app.domain.assistant.site_map import find_page, find_page_for_message, site_pages_prompt_block
from app.domain.interview.member_spec_retrieval import build_member_spec_context
from app.domain.resume._shared import parse_json_response

_NO_KEY_MESSAGE = "챗봇을 사용하려면 GEMINI_API_KEY 설정이 필요합니다."
_NO_MESSAGE_MESSAGE = "메시지를 입력해주세요."
_PARSE_FAIL_MESSAGE = "답변을 정리하지 못했어요. 잠시 후 다시 시도해 주세요."
_FALLBACK_REPLY = "죄송해요, 답변을 만들지 못했어요. 다시 한 번 말씀해주시겠어요?"

# 프롬프트에 넣는 이전 대화는 최근 N턴까지만 - 너무 길면 토큰만 늘고 최신 맥락 파악에는
# 오히려 방해가 된다(사람도 챗봇도 최근 대화가 제일 중요하다).
_MAX_HISTORY_TURNS = 10


@dataclass
class AssistantReply:
    ok: bool
    message: str | None = None
    reply: str | None = None
    navigate_to: str | None = None
    suggested_navigate_to: str | None = None
    # 이동 전 프론트가 띄우는 미리보기 카드 재료(site_map.SitePage.to_dict()).
    # suggested_navigate_to가 살아남았을 때만 채워진다.
    suggested_page: dict | None = None
    job_references: list[dict] | None = None

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "reply": self.reply,
            "navigate_to": self.navigate_to,
            "suggested_navigate_to": self.suggested_navigate_to,
            "suggested_page": self.suggested_page,
            "job_references": self.job_references or [],
        }


def _history_block(history: list[dict]) -> str:
    trimmed = history[-_MAX_HISTORY_TURNS:]
    lines = []
    for turn in trimmed:
        role = "사용자" if turn.get("role") == "user" else "챗봇"
        content = str(turn.get("content") or "").strip()
        if content:
            lines.append(f"{role}: {content}")
    return "\n".join(lines)


def chat(message: str, history: list[dict] | None = None, member_id: int | None = None) -> AssistantReply:
    history = history or []
    message = message.strip()

    if not settings.gemini_api_key:
        return AssistantReply(ok=False, message=_NO_KEY_MESSAGE)
    if not message:
        return AssistantReply(ok=False, message=_NO_MESSAGE_MESSAGE)

    history_text = _history_block(history)
    knowledge_text = knowledge.knowledge_prompt_block(message)
    member_spec_text = build_member_spec_context(member_id) if member_id else None
    job_matches: list[JobMatchReference] = (
        fetch_active_matches(member_id) if member_id and is_job_question(message) else []
    )
    job_matches_text = job_matches_prompt_block(job_matches)

    prompt = (
        "당신은 한국 취업 준비생을 위한 채용/커리어 플랫폼 'Job-A-Dream AI'의 사이트 도우미 "
        "챗봇입니다. 사용자의 질문에 친절하고 간결하게 답하고, 사이트 이용법이나 채용/취업/"
        "이력서/면접 관련 조언도 해줄 수 있습니다.\n\n"
        "아래는 이 사이트에서 실제로 존재하는 페이지 목록과, 각 페이지에서 볼 수 있는 내용(· 줄)입니다. "
        "사용자가 이동해 달라고 명시적으로 요청한 경우에도 바로 이동시키지 말고, suggested_navigate_to에 "
        "경로만 넣은 뒤 reply에서 그 페이지에 무엇이 있는지 미리 알려주고 마지막에 이동 여부를 물어보세요.\n\n"
        f"[사이트 페이지 목록]\n{site_pages_prompt_block()}\n\n"
        + (f"[사이트 지식 참고자료 - 사용자 질문과 관련 있을 수 있는 이 사이트의 실제 "
           f"정책/기능. 여기 없는 내용은 지어내지 말고, 정말 모르면 모른다고 답하세요]\n"
           f"{knowledge_text}\n\n" if knowledge_text else "")
        + (f"[이전 대화]\n{history_text}\n\n" if history_text else "")
        + (f"[현재 회원이 직접 저장한 스펙 - 이 회원의 정보만 사용]\n{member_spec_text}\n\n"
           if member_spec_text else "")
        + (f"{job_matches_text}\n\n" if job_matches_text else "")
        + f"[사용자 메시지]\n{message}\n\n"
        "[작성 규칙]\n"
        "1. reply는 사용자 메시지에 대한 자연스러운 한국어 답변이다 - 존댓말, 2~5문장 "
        "내외로 간결하게 작성해라\n"
        "2. navigate_to는 항상 null로 써라. 사용자 동의 전에는 절대 자동 이동하지 않는다.\n"
        "3. reply는 채팅 안에서 답을 끝내라. 아래 근거 자료로 답할 수 있는 질문을 '그 페이지에 "
        "가보세요'로 떠넘기지 마라 - 이동은 답을 대체하는 게 아니라 '더 자세히 보고 싶으면' 고르는 "
        "선택지다. suggested_navigate_to는 위 페이지 목록에 있는 경로 문자열 그대로만 쓰거나 null로 "
        "쓰고, 답변 내용을 더 자세히 볼 수 있는 페이지가 있으면 채워라.\n"
        "4. suggested_navigate_to를 채웠어도 reply는 (a) 질문에 대한 답을 그 자리에서 완결되게 하고 "
        "(b) 그 페이지에서 더 볼 수 있는 것을 위 목록의 · 줄을 근거로 한두 가지 덧붙이는 순서다. "
        "이동 여부를 묻는 문장은 reply에 쓰지 마라 - 화면에 예/아니오 버튼이 따로 붙는다. "
        "· 줄에 없는 기능을 그 페이지에 있는 것처럼 지어내지 마라.\n"
        "5. [사이트 지식 참고자료]가 있으면 그 내용을 우선 근거로 답해라 - 참고자료와 "
        "다른 내용을 지어내지 마라. 참고자료가 없는데 사이트 고유 정책(요금, 절차 등)을 "
        "묻는 질문이면 확신 없이 단정하지 말고 정확한 정보는 사이트에서 직접 확인해달라고 "
        "안내해라\n"
        "6. [현재 회원이 직접 저장한 스펙]이 있으면 그 회원의 이력서·기술·프로젝트에 관한 질문에만 "
        "참고하고, 정보에 없는 경력이나 성과는 지어내지 마라\n"
        "7. [현재 회원의 모집 중 매칭 공고]가 있으면 공고 추천/지원 질문에는 그 목록의 공고를 "
        "회사명·적합도와 함께 reply에서 직접 소개해라(같은 공고 카드가 답변 아래에 함께 표시된다). "
        "적합도·미확인 필수요건만 근거로 설명하고, 목록 밖 공고, 마감 여부, 자격 충족을 지어내지 마라\n"
        "8. 매칭 공고가 없으면 공고를 지어내지 말고 맞춤 채용공고 페이지에서 스펙을 저장하거나 "
        "매칭을 갱신해 달라고 안내해라\n"
        "9. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "reply": "문장",\n'
        '  "navigate_to": null,\n'
        '  "suggested_navigate_to": "/path" 또는 null\n'
        "}"
    )

    try:
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=settings.gemini_api_key)
        response = client.models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type="application/json"),
        )
        data = parse_json_response(response.text or "")
        if data is None:
            return AssistantReply(ok=False, message=_PARSE_FAIL_MESSAGE)

        reply = str(data.get("reply") or "").strip() or _FALLBACK_REPLY
        navigate_to = data.get("navigate_to")
        navigate_to = str(navigate_to).strip() if navigate_to else None
        # Navigation is a two-step interaction. The server never returns a direct
        # destination; the widget stores only a validated suggestion and asks for consent.
        suggested_navigate_to = data.get("suggested_navigate_to")
        suggested_navigate_to = str(suggested_navigate_to).strip() if suggested_navigate_to else None
        # find_page()는 is_known_path()와 같은 목록을 본다 - 목록에 없는 경로면 None이 되어
        # 제안 자체가 사라지므로, 프론트가 없는 페이지의 미리보기를 띄울 일도 없다.
        #
        # Gemini가 그 칸을 비워 보내면 find_page_for_message()가 사용자 메시지에서 직접
        # 찾는다. 이 폴백이 없던 동안은 이동 제안이 모델 응답 하나에 통째로 달려 있어서,
        # 모델이 칸을 안 채우면 미리보기도 예/아니오 버튼도 안 뜨고 답변만 "이동할까요?"라고
        # 물어보는 상태가 됐다(사용자가 "네"라고 해도 아무 일도 안 일어남).
        suggested_page = find_page(suggested_navigate_to) or find_page_for_message(message)
        # 적합도 순 매칭 공고를 보여준 답변에는 항상 맞춤 채용 분석 화면을 붙인다 - 채팅에
        # 실리는 건 상위 3개뿐이라 "자세히 보려면" 갈 곳이 확실하기 때문이다. 공고 카드
        # 자체는 그대로 남는다(카드를 안내로 대체하면 안 된다).
        if job_matches:
            suggested_page = find_page("/dashboard") or suggested_page
        suggested_navigate_to = suggested_page.path if suggested_page else None

        return AssistantReply(
            ok=True,
            reply=reply,
            navigate_to=None,
            suggested_navigate_to=suggested_navigate_to,
            suggested_page=suggested_page.to_dict() if suggested_page else None,
            job_references=[match.to_dict() for match in job_matches],
        )
    except Exception as e:
        return AssistantReply(
            ok=False, message=f"챗봇 응답에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
