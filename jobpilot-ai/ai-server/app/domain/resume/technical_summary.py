"""이력서 작성 도우미 - CareerProfile "반영" 방향 (태스크 #63).

self_introduction.py/project.py는 "CareerProfile -> 이력서" 방향(불러오기)을 위해 job/
tech_summary를 컨텍스트로 받기만 했다. 이 모듈은 반대 방향 - 회원이 자기소개서/프로젝트
경험을 저장할 때마다, 그동안 쌓인 이력서 내용 전체를 다시 훑어서 MemberSpecification.
technicalSummary(기술 요약)를 최신 내용으로 다시 써준다("반영"). 항목 하나를 그대로 복사해
넣는 게 아니라 Gemini가 짧은 요약 문단으로 재합성하는 이유: technicalSummary는 모의면접
질문 생성(question_generator.py)에서 컨텍스트로 쓰이는 필드라 자기소개서/프로젝트 원문처럼
길면 오히려 질문 품질이 떨어진다 - 2~4문장 수준으로 압축된 요약이어야 한다.

기존 self_introduction.py/project.py와 같은 fail-open 원칙 - Gemini 키가 없거나 호출이
실패하면 ok=False만 반환하고 예외를 던지지 않는다(호출하는 Spring 쪽에서 실패해도 기존
technicalSummary를 그대로 두면 되게).
"""

from dataclasses import dataclass

from app.core.config import settings

_NO_KEY_MESSAGE = "기술 요약 자동 반영을 사용하려면 GEMINI_API_KEY 설정이 필요합니다."
_NO_CONTENT_MESSAGE = "반영할 자기소개서/프로젝트 내용이 없습니다."
_EMPTY_RESULT_MESSAGE = "AI 응답이 비어 있습니다. 잠시 후 다시 시도해 주세요."


@dataclass
class TechnicalSummaryResult:
    ok: bool
    message: str | None = None
    summary: str | None = None

    def to_dict(self) -> dict:
        return {"ok": self.ok, "message": self.message, "summary": self.summary}


def _project_block(index: int, project: dict) -> str:
    title = str(project.get("title") or "").strip()
    parts = [
        str(project.get(key) or "").strip()
        for key in ("role_description", "problem_description", "solution_description", "result_description")
    ]
    body = "\n".join(p for p in parts if p)
    header = f"[프로젝트 {index}] {title}" if title else f"[프로젝트 {index}]"
    return f"{header}\n{body}" if body else header


def synthesize(
    job: str = "",
    existing_summary: str = "",
    self_introductions: list[str] | None = None,
    projects: list[dict] | None = None,
) -> TechnicalSummaryResult:
    """자기소개서 본문들 + 프로젝트(STAR) 필드들을 모두 훑어서 기술 요약 한 문단을
    새로 합성한다. existing_summary는 참고 컨텍스트로만 주고, 그대로 이어붙이지는 않는다 -
    이미 반영된 옛 내용과 새 내용이 중복/모순되지 않고 하나로 정리되게 하기 위함."""
    self_introductions = [s.strip() for s in (self_introductions or []) if s and s.strip()]
    projects = projects or []

    if not settings.gemini_api_key:
        return TechnicalSummaryResult(ok=False, message=_NO_KEY_MESSAGE)
    if not self_introductions and not projects:
        return TechnicalSummaryResult(ok=False, message=_NO_CONTENT_MESSAGE)

    intro_text = "\n\n".join(f"[자기소개서 {i}]\n{s}" for i, s in enumerate(self_introductions, start=1))
    project_text = "\n\n".join(_project_block(i, p) for i, p in enumerate(projects, start=1))
    source_text = "\n\n".join(t for t in (intro_text, project_text) if t)

    prompt = (
        "당신은 한국 채용 시장에 정통한 이력서 컨설턴트입니다. 아래 지원자가 작성한 "
        "자기소개서/프로젝트 경험 원문을 읽고, 이 지원자의 '기술 요약'을 새로 작성해주세요. "
        "이 요약은 모의면접 질문을 만들 때 컨텍스트로 쓰이므로, 실제 기술 스택/도구/경험한 "
        "문제 영역이 구체적으로 드러나야 합니다.\n\n"
        f"[목표 직무] {job or '미지정'}\n"
        + (f"[기존 기술 요약(참고용, 그대로 베끼지 말고 아래 원문 기준으로 다시 정리)]\n{existing_summary.strip()}\n\n" if existing_summary.strip() else "\n")
        + f"[자기소개서/프로젝트 원문]\n{source_text}\n\n"
        "[작성 규칙]\n"
        "1. 원문에 실제로 언급된 기술/도구/경험만 반영해라 - 원문에 없는 기술 스택이나 "
        "성과를 지어내지 마라\n"
        "2. 2~4문장, 300자 내외의 한 문단으로 작성해라 - 목록이나 줄바꿈 없이 이어지는 "
        "문장으로 써라\n"
        "3. '~함', '~했음' 같은 개조식이 아니라 '~했습니다/합니다' 같은 요약 문단 톤으로 "
        "써라\n"
        "4. 마크다운, 따옴표, 설명 없이 완성된 문단 텍스트만 출력해라\n"
    )

    try:
        from google import genai

        client = genai.Client(api_key=settings.gemini_api_key)
        response = client.models.generate_content(model=settings.gemini_model, contents=prompt)
        summary = (response.text or "").strip()
        if not summary:
            return TechnicalSummaryResult(ok=False, message=_EMPTY_RESULT_MESSAGE)
        return TechnicalSummaryResult(ok=True, summary=summary)
    except Exception as e:
        return TechnicalSummaryResult(
            ok=False, message=f"기술 요약 반영에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
