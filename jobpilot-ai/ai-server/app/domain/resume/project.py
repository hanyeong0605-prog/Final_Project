"""이력서 작성 도우미 - 프로젝트 경험(STAR) 질문식 작성 + 첨삭 (Gemini).

self_introduction.py와 같은 원칙(저장은 backend의 Project CRUD가 담당, 여기는 생성/첨삭만)
이지만, Project 엔티티는 자기소개서와 달리 role/problem/solution/result 4개의 독립된 텍스트
컬럼(STAR - 상황/역할, 과제/문제, 해결, 결과)으로 나뉘어 있다. 그래서 generate_draft()는
self_introduction.generate_draft()처럼 문단 하나를 반환하는 게 아니라, 4개 필드를 각각
다듬어서 JSON으로 반환한다 - 프론트가 그 값을 그대로 ProjectRequest의 4개 필드에 채워
저장하면 된다.
"""

from dataclasses import dataclass, field

from app.core.config import settings
from app.domain.resume._shared import as_str_list, parse_json_response

_NO_KEY_MESSAGE = "프로젝트 경험 작성 도우미를 사용하려면 GEMINI_API_KEY 설정이 필요합니다."
_PARSE_FAIL_MESSAGE = "AI 응답을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요."
_NO_ANSWER_MESSAGE = "답변을 하나 이상 입력해주세요."
_NO_CONTENT_MESSAGE = "첨삭받을 프로젝트 설명을 입력해주세요."

# STAR(Situation/Task - Role, Action - Solution, Result) 구조를 그대로 4개 질문으로 뒀다 -
# 순서와 의미가 Project 엔티티의 role/problem/solution/result 컬럼과 1:1로 대응한다.
GUIDED_QUESTIONS: tuple[str, ...] = (
    "이 프로젝트에서 맡으신 역할은 무엇이었나요? (팀 규모, 담당 파트, 사용한 기술 스택 등)",
    "프로젝트를 진행하면서 어떤 문제나 어려움을 겪으셨나요?",
    "그 문제를 어떻게 해결하셨나요? (구체적인 접근 방법이나 기술적 선택 이유)",
    "그 결과 어떤 성과가 있었나요? (가능하면 정량적인 수치와 함께)",
)
_FIELD_KEYS = ("role_description", "problem_description", "solution_description", "result_description")


@dataclass
class ProjectDraft:
    ok: bool
    message: str | None = None
    role_description: str | None = None
    problem_description: str | None = None
    solution_description: str | None = None
    result_description: str | None = None

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "role_description": self.role_description,
            "problem_description": self.problem_description,
            "solution_description": self.solution_description,
            "result_description": self.result_description,
        }


@dataclass
class ProjectCritique:
    ok: bool
    message: str | None = None
    strengths: list[str] = field(default_factory=list)
    improvements: list[str] = field(default_factory=list)
    revised_example: str | None = None

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "strengths": self.strengths,
            "improvements": self.improvements,
            "revised_example": self.revised_example,
        }


def _career_context(job: str, tech_summary: str) -> str:
    lines = [f"[목표 직무] {job or '미지정'}"]
    if tech_summary.strip():
        lines.append(f"[기술/프로젝트 요약] {tech_summary.strip()}")
    return "\n".join(lines) + "\n\n"


def generate_draft(
    title: str = "", job: str = "", tech_summary: str = "", answers: list[str] | None = None
) -> ProjectDraft:
    """GUIDED_QUESTIONS(STAR 4개) 순서에 맞춘 답변을 받아, role/problem/solution/result
    4개 필드 각각을 자연스러운 이력서 문장으로 다듬어 JSON으로 반환한다. self_introduction과
    같은 이유로 답변에 없는 내용을 지어내지 말라고 명시한다."""
    answers = answers or []
    if not settings.gemini_api_key:
        return ProjectDraft(ok=False, message=_NO_KEY_MESSAGE)
    if not any(a.strip() for a in answers):
        return ProjectDraft(ok=False, message=_NO_ANSWER_MESSAGE)

    qa_blocks = [
        f"[질문] {q}\n[답변] {a.strip()}"
        for q, a in zip(GUIDED_QUESTIONS, answers)
        if a.strip()
    ]
    qa_text = "\n\n".join(qa_blocks)

    prompt = (
        "당신은 한국 채용 시장에 정통한 이력서 컨설턴트입니다. 아래 지원자 정보와 프로젝트 "
        "경험에 대한 질문별 답변을 참고해서, 이력서/포트폴리오에 쓸 수 있는 STAR(상황·역할 - "
        "문제 - 해결 - 결과) 구조의 프로젝트 설명을 작성해주세요.\n\n"
        f"{_career_context(job, tech_summary)}"
        f"[프로젝트명] {title or '미지정'}\n\n"
        f"[질문과 답변]\n{qa_text}\n\n"
        "[작성 규칙]\n"
        "1. 답변에 없는 경험이나 성과 수치를 지어내지 마라 - 답변 내용을 자연스러운 문장으로 "
        "다듬는 것이지, 새로운 내용을 창작하는 게 아니다\n"
        "2. 각 필드는 1~3문장, 문어체 존댓말로 간결하고 구체적으로 작성해라\n"
        "3. 답변이 없는 항목(질문과 답변에 없는 필드)은 빈 문자열로 남겨둬라 - 억지로 채우지 "
        "마라\n"
        "4. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "role_description": "문장",\n'
        '  "problem_description": "문장",\n'
        '  "solution_description": "문장",\n'
        '  "result_description": "문장"\n'
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
            return ProjectDraft(ok=False, message=_PARSE_FAIL_MESSAGE)
        values = {key: (str(data.get(key) or "").strip() or None) for key in _FIELD_KEYS}
        return ProjectDraft(ok=True, **values)
    except Exception as e:
        return ProjectDraft(
            ok=False, message=f"프로젝트 설명 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )


def critique(
    role_description: str = "",
    problem_description: str = "",
    solution_description: str = "",
    result_description: str = "",
    job: str = "",
    tech_summary: str = "",
) -> ProjectCritique:
    """이미 작성된 프로젝트 설명(4개 필드 중 채워진 것만)을 받아 강점/개선점/수정 예시를
    JSON으로 반환한다."""
    fields = {
        "역할": role_description,
        "문제": problem_description,
        "해결": solution_description,
        "결과": result_description,
    }
    if not settings.gemini_api_key:
        return ProjectCritique(ok=False, message=_NO_KEY_MESSAGE)
    if not any(v.strip() for v in fields.values()):
        return ProjectCritique(ok=False, message=_NO_CONTENT_MESSAGE)

    content_text = "\n".join(f"[{label}] {value.strip()}" for label, value in fields.items() if value.strip())

    prompt = (
        "당신은 한국 채용 시장에 정통한 이력서 컨설턴트입니다. 아래 지원자의 프로젝트 경험 "
        "설명을 읽고 첨삭 의견을 정해진 JSON 형식으로만 응답하세요.\n\n"
        f"{_career_context(job, tech_summary)}"
        f"[프로젝트 설명]\n{content_text}\n\n"
        "[작성 규칙]\n"
        "1. strengths(잘 쓴 부분)는 1~3개, improvements(고치면 좋을 부분)는 1~5개, 각각 "
        "짧고 구체적인 한국어 문장으로 작성해라(60자 내외) - 특히 정량적 성과(수치)가 "
        "빠져있으면 반드시 improvements에 지적해라\n"
        "2. revised_example은 가장 개선이 필요한 필드 하나만 골라 개선 방향을 반영해서 다시 "
        "쓴 예시로 작성해라(150자 내외) - 원문에 없는 사실을 지어내지 마라\n"
        "3. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "strengths": ["문장", ...],\n'
        '  "improvements": ["문장", ...],\n'
        '  "revised_example": "문장"\n'
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
            return ProjectCritique(ok=False, message=_PARSE_FAIL_MESSAGE)
        return ProjectCritique(
            ok=True,
            strengths=as_str_list(data.get("strengths")),
            improvements=as_str_list(data.get("improvements")),
            revised_example=(str(data.get("revised_example") or "").strip() or None),
        )
    except Exception as e:
        return ProjectCritique(
            ok=False, message=f"프로젝트 설명 첨삭에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
