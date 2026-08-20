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
"""

from dataclasses import dataclass

from app.core.config import settings
from app.domain.assistant import knowledge
from app.domain.assistant.site_map import is_known_path, site_pages_prompt_block
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

    def to_dict(self) -> dict:
        return {"ok": self.ok, "message": self.message, "reply": self.reply, "navigate_to": self.navigate_to}


def _history_block(history: list[dict]) -> str:
    trimmed = history[-_MAX_HISTORY_TURNS:]
    lines = []
    for turn in trimmed:
        role = "사용자" if turn.get("role") == "user" else "챗봇"
        content = str(turn.get("content") or "").strip()
        if content:
            lines.append(f"{role}: {content}")
    return "\n".join(lines)


def chat(message: str, history: list[dict] | None = None) -> AssistantReply:
    history = history or []
    message = message.strip()

    if not settings.gemini_api_key:
        return AssistantReply(ok=False, message=_NO_KEY_MESSAGE)
    if not message:
        return AssistantReply(ok=False, message=_NO_MESSAGE_MESSAGE)

    history_text = _history_block(history)
    knowledge_text = knowledge.knowledge_prompt_block(message)

    prompt = (
        "당신은 한국 취업 준비생을 위한 채용/커리어 플랫폼 'Job-A-Dream AI'의 사이트 도우미 "
        "챗봇입니다. 사용자의 질문에 친절하고 간결하게 답하고, 사이트 이용법이나 채용/취업/"
        "이력서/면접 관련 조언도 해줄 수 있습니다.\n\n"
        "아래는 이 사이트에서 실제로 존재하는 페이지 목록입니다 - 사용자가 특정 기능이나 "
        "페이지로 이동하고 싶어하는 의도가 분명하면(예: '이력서 쓰고 싶어', '채용공고 보여줘') "
        "navigate_to에 그 페이지 경로를 정확히 채워주세요. 단순 질문이나 이동 의도가 없으면 "
        "navigate_to는 null로 두세요.\n\n"
        f"[사이트 페이지 목록]\n{site_pages_prompt_block()}\n\n"
        + (f"[사이트 지식 참고자료 - 사용자 질문과 관련 있을 수 있는 이 사이트의 실제 "
           f"정책/기능. 여기 없는 내용은 지어내지 말고, 정말 모르면 모른다고 답하세요]\n"
           f"{knowledge_text}\n\n" if knowledge_text else "")
        + (f"[이전 대화]\n{history_text}\n\n" if history_text else "")
        + f"[사용자 메시지]\n{message}\n\n"
        "[작성 규칙]\n"
        "1. reply는 사용자 메시지에 대한 자연스러운 한국어 답변이다 - 존댓말, 2~4문장 "
        "내외로 간결하게 작성해라\n"
        "2. navigate_to는 위 페이지 목록에 있는 경로 문자열 그대로만 쓰거나, 이동 의도가 "
        "없으면 null로 써라 - 목록에 없는 경로를 지어내지 마라\n"
        "3. navigate_to를 채웠다면 reply에도 어디로 안내하는지 자연스럽게 언급해라(예: "
        "'이력서 작성 도우미 페이지로 안내해드릴게요!')\n"
        "4. [사이트 지식 참고자료]가 있으면 그 내용을 우선 근거로 답해라 - 참고자료와 "
        "다른 내용을 지어내지 마라. 참고자료가 없는데 사이트 고유 정책(요금, 절차 등)을 "
        "묻는 질문이면 확신 없이 단정하지 말고 정확한 정보는 사이트에서 직접 확인해달라고 "
        "안내해라\n"
        "5. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "reply": "문장",\n'
        '  "navigate_to": "/path" 또는 null\n'
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
        if not is_known_path(navigate_to):
            navigate_to = None  # 목록에 없는 경로는 절대 프론트로 내보내지 않는다

        return AssistantReply(ok=True, reply=reply, navigate_to=navigate_to)
    except Exception as e:
        return AssistantReply(
            ok=False, message=f"챗봇 응답에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
