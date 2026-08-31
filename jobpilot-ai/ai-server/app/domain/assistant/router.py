"""사이트 전역 챗봇 API - SiteAssistantWidget이 부르는 유일한 엔드포인트.

프론트에서 로그인한 회원만 이 위젯을 볼 수 있다(AppShell이 RequireAuth 하위에 있음) - 이
엔드포인트 자체는 다른 resume/interview 엔드포인트와 마찬가지로 별도 인증 없이 열려 있다
(기존 원칙 유지, chat.py docstring 참고).
"""

import secrets

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

from app.core.config import settings
from app.domain.assistant.chat import chat as run_chat

router = APIRouter()


class ChatTurn(BaseModel):
    role: str  # "user" 또는 "assistant"
    content: str = ""


class AssistantChatRequest(BaseModel):
    message: str
    history: list[ChatTurn] = []
    member_id: int | None = None


@router.post("/chat")
def assistant_chat(body: AssistantChatRequest, internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key")):
    # A member ID is trusted only after Spring has validated the caller's JWT and
    # forwarded it over the private Docker network with the shared internal key.
    if not settings.internal_api_key or not internal_api_key or not secrets.compare_digest(
        internal_api_key, settings.internal_api_key
    ):
        raise HTTPException(status_code=403, detail="Internal assistant access is required.")
    result = run_chat(
        message=body.message,
        history=[t.model_dump() for t in body.history],
        member_id=body.member_id,
    )
    return result.to_dict()
