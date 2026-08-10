"""개인 타임라인 - 누적 인사이트 (태스크 #69, "유료 결제 전제" 기능).

2026-08-10: 사용자가 "유료 결제했다는 전제하에 타임라인이랑 맞춤형[질문/제안]"이라고 명시한
기능 - 실제 결제 게이트(크레딧 차감 등, 태스크 #40~44)는 아직 없으니 지금은 항상 노출한다.
결제 기능이 붙으면 프론트에서 이 API를 부르기 전에 크레딧/구독 여부만 확인하면 되고, 이
모듈 자체는 손댈 필요 없다(관심사 분리).

resume 도메인(self_introduction.py, project.py)과 같은 fail-open 원칙 - 이 모듈도 저장은
안 하고 생성만 한다. 여러 세션에 걸쳐 "반복되는 개선점"을 찾아내는 게 핵심이라, 세션이
2개 미만이면(비교할 대상이 없음) Gemini를 호출하지 않고 안내 메시지만 반환한다.
"""

from dataclasses import dataclass, field

from app.core.config import settings
from app.domain.resume._shared import as_str_list, parse_json_response

_NO_KEY_MESSAGE = "인사이트를 보려면 GEMINI_API_KEY 설정이 필요합니다."
_NOT_ENOUGH_SESSIONS_MESSAGE = "모의면접 기록이 2개 이상 쌓이면 반복되는 패턴을 분석해드려요."
_PARSE_FAIL_MESSAGE = "인사이트를 정리하지 못했어요. 잠시 후 다시 시도해 주세요."

_MIN_SESSIONS = 2
# 너무 오래된 세션까지 다 넣으면 "최근 경향"이 아니라 "역대 전체 평균" 같은 밍밍한 결과가
# 나온다 - 최근 몇 개만 봐야 "요즘 반복되는 문제"를 짚어낼 수 있다.
_MAX_SESSIONS_CONSIDERED = 8


@dataclass
class TimelineInsight:
    ok: bool
    message: str | None = None
    recurring_points: list[str] = field(default_factory=list)
    resume_linked_suggestion: str | None = None

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "recurring_points": self.recurring_points,
            "resume_linked_suggestion": self.resume_linked_suggestion,
        }


def _session_block(index: int, session: dict) -> str:
    role = str(session.get("role") or "").strip()
    interview_type = str(session.get("interview_type") or "").strip()
    score = session.get("overall_score")
    improvements = as_str_list(session.get("improvements"))
    header = f"[세션 {index}]" + (f" {role}" if role else "") + (f" / {interview_type}" if interview_type else "")
    header += f" / 총평 {score}점" if score is not None else ""
    body = "개선점: " + ", ".join(improvements) if improvements else "개선점: (기록 없음)"
    return f"{header}\n{body}"


def _resume_block(self_introductions: list[str], projects: list[dict]) -> str:
    parts = []
    for i, s in enumerate(self_introductions, start=1):
        if s.strip():
            parts.append(f"[자기소개서 {i}]\n{s.strip()}")
    for i, p in enumerate(projects, start=1):
        fields = [str(p.get(k) or "").strip() for k in
                   ("role_description", "problem_description", "solution_description", "result_description")]
        body = "\n".join(f for f in fields if f)
        if body:
            title = str(p.get("title") or "").strip()
            parts.append(f"[프로젝트 {i}] {title}\n{body}")
    return "\n\n".join(parts)


def generate_insight(
    sessions: list[dict] | None = None,
    self_introductions: list[str] | None = None,
    projects: list[dict] | None = None,
) -> TimelineInsight:
    sessions = (sessions or [])[:_MAX_SESSIONS_CONSIDERED]
    self_introductions = self_introductions or []
    projects = projects or []

    if not settings.gemini_api_key:
        return TimelineInsight(ok=False, message=_NO_KEY_MESSAGE)
    if len(sessions) < _MIN_SESSIONS:
        return TimelineInsight(ok=False, message=_NOT_ENOUGH_SESSIONS_MESSAGE)

    sessions_text = "\n\n".join(_session_block(i, s) for i, s in enumerate(sessions, start=1))
    resume_text = _resume_block(self_introductions, projects)

    prompt = (
        "당신은 한국 취업 준비생을 위한 모의면접 코치입니다. 아래는 한 지원자가 최근에 "
        "진행한 모의면접 세션들의 기록(세션마다 개선점 포함)과, 이 지원자가 작성한 이력서 "
        "내용(자기소개서/프로젝트 경험)입니다.\n\n"
        f"[최근 모의면접 세션 기록]\n{sessions_text}\n\n"
        + (f"[이력서 내용]\n{resume_text}\n\n" if resume_text else "[이력서 내용]\n(작성된 내용 없음)\n\n")
        + "[작성 규칙]\n"
        "1. recurring_points: 여러 세션에 걸쳐 반복적으로 지적된 개선점을 1~4개 뽑아라 - "
        "한 세션에만 나온 건 제외하고, 정말 반복되는 패턴만 골라라. 각 문장은 구체적으로 "
        "무엇을 어떻게 고치라는 건지 밝혀라(60자 내외)\n"
        "2. resume_linked_suggestion: 위 이력서 내용을 참고해서, 이 지원자가 답변할 때 더 "
        "활용하면 좋을 구체적인 경험/사례를 하나 짚어 제안해라(이력서 내용이 없으면 "
        "'이력서를 먼저 작성하면 더 구체적인 제안을 받을 수 있어요' 같은 안내로 대체해라). "
        "150자 내외 한 문단으로 작성해라\n"
        "3. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "recurring_points": ["문장", ...],\n'
        '  "resume_linked_suggestion": "문단"\n'
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
            return TimelineInsight(ok=False, message=_PARSE_FAIL_MESSAGE)
        return TimelineInsight(
            ok=True,
            recurring_points=as_str_list(data.get("recurring_points")),
            resume_linked_suggestion=(str(data.get("resume_linked_suggestion") or "").strip() or None),
        )
    except Exception as e:
        return TimelineInsight(
            ok=False, message=f"인사이트 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
