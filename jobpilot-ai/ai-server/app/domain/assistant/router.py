"""사이트 전역 챗봇 API - SiteAssistantWidget이 부르는 유일한 엔드포인트.

프론트에서 로그인한 회원만 이 위젯을 볼 수 있다(AppShell이 RequireAuth 하위에 있음) - 이
엔드포인트 자체는 다른 resume/interview 엔드포인트와 마찬가지로 별도 인증 없이 열려 있다
(기존 원칙 유지, chat.py docstring 참고).
"""

from fastapi import APIRouter
from pydantic import BaseModel

from app.domain.assistant.chat import chat as run_chat

router = APIRouter()


class ChatTurn(BaseModel):
    role: str  # "user" 또는 "assistant"
    content: str = ""


class AssistantChatRequest(BaseModel):
    message: str
    history: list[ChatTurn] = []


@router.post("/chat")
def assistant_chat(body: AssistantChatRequest):
    result = run_chat(message=body.message, history=[t.model_dump() for t in body.history])
    return result.to_dict()
