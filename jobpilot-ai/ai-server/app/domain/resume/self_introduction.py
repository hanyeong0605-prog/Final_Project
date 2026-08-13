"""이력서 작성 도우미 - 자기소개서 질문식 작성 + 첨삭 (Gemini).

2026-08-10 설계 메모: 회원의 이력서 관련 엔티티(자기소개서, 프로젝트 경험)는 이미
backend(Spring/MySQL)에 있다 - 이 모듈은 저장을 하지 않고, "질문식 작성"(고정 질문에 대한
답변을 자연스러운 자기소개서 문단으로 다듬기)과 "첨삭"(이미 쓴 글에 대한 강점/개선점/수정
예시)만 담당한다. 기존 모의면접 기능과 같은 원칙 - ai-server는 생성만, Spring이 영속성을
맡는다(프론트가 이 결과를 받아서 별도로 POST/PUT /api/v1/members/me/self-introductions를
호출해서 저장).

목표직무/기술요약(MemberCareerProfile)은 프론트가 기존 GET /career-profile로 먼저 읽어와서
job/tech_summary로 이 모듈에 넘겨준다 - ai-server가 직접 Spring DB를 조회하지 않는다(기존
모의면접 질문 생성과 동일한 컨텍스트 전달 방식, question_generator.py 참고).

Gemini 키가 없거나 호출이 실패하면 fail-open으로 ok=False + 안내 메시지만 채워서 반환한다
(evaluation.py의 EvaluationReport와 같은 패턴) - 예외를 던지지 않는다.
"""

from dataclasses import dataclass, field

from app.core.config import settings
from app.domain.resume._shared import as_str_list, parse_json_response

_NO_KEY_MESSAGE = "자기소개서 작성 도우미를 사용하려면 GEMINI_API_KEY 설정이 필요합니다."
_PARSE_FAIL_MESSAGE = "AI 응답을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요."
_NO_ANSWER_MESSAGE = "답변을 하나 이상 입력해주세요."
_NO_CONTENT_MESSAGE = "첨삭받을 자기소개서 내용을 입력해주세요."
_NO_RAW_TEXT_MESSAGE = "회사 자소서 양식 텍스트를 입력해주세요."

# 2026-08-10: 자기소개서에서 채용 담당자가 실제로 보는 항목(지원동기/성장과정·가치관/
# 강점·약점/입사 후 포부)을 그대로 고정 질문 4개로 뒀다 - AI가 매번 다른 질문을 생성하게
# 하면 다양성은 늘지만 "이력서 작성"이라는 목적상 검증된 표준 항목을 놓치는 게 더 위험하다고
# 판단했다(모의면접 질문 생성과 다른 판단 기준 - 거긴 다양성이 핵심 가치였지만 여긴 완성도).
GUIDED_QUESTIONS: tuple[str, ...] = (
    "이 직무/회사에 지원하시게 된 동기가 무엇인가요? 어떤 계기로 관심을 갖게 되셨나요?",
    "성장 과정에서 지금의 가치관이나 일하는 태도를 형성하게 된 경험이 있다면 말씀해주세요.",
    "본인의 강점과 약점은 무엇이고, 약점을 보완하기 위해 어떤 노력을 하고 계신가요?",
    "입사 후 이루고 싶은 목표나 포부가 있다면 말씀해주세요.",
)


@dataclass
class SelfIntroductionDraft:
    ok: bool
    message: str | None = None
    content: str | None = None  # 완성된 자기소개서 본문

    def to_dict(self) -> dict:
        return {"ok": self.ok, "message": self.message, "content": self.content}


@dataclass
class SelfIntroductionCritique:
    ok: bool
    message: str | None = None
    strengths: list[str] = field(default_factory=list)
    improvements: list[str] = field(default_factory=list)
    revised_example: str | None = None  # 개선 방향을 반영한 일부 수정 예시(전체 재작성 아님)

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "strengths": self.strengths,
            "improvements": self.improvements,
            "revised_example": self.revised_example,
        }


@dataclass
class CompanyQuestionsResult:
    ok: bool
    message: str | None = None
    questions: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {"ok": self.ok, "message": self.message, "questions": self.questions}


# 2026-08-13: "자소서 회사 양식이 있으면 그거에 대해 필요한 질문 물어보고" 요청으로 추가 -
# 회사 채용 페이지에서 그대로 복사-붙여넣기한 자소서 문항 텍스트(번호/글자수 제한/안내
# 문구가 뒤섞여 있는 경우가 많음)를 받아서, 실제로 물어야 할 질문 문장만 깔끔하게 추출한다.
# 파싱 실패/키 없음이면 빈 리스트를 반환(fail-open) - 호출부(프론트)가 빈 리스트를 받으면
# 기존 GUIDED_QUESTIONS(범용 4문항)로 조용히 폴백한다.
def parse_company_questions(raw_text: str) -> CompanyQuestionsResult:
    if not settings.gemini_api_key:
        return CompanyQuestionsResult(ok=False, message=_NO_KEY_MESSAGE)
    if not raw_text.strip():
        return CompanyQuestionsResult(ok=False, message=_NO_RAW_TEXT_MESSAGE)

    prompt = (
        "다음은 채용 사이트에서 그대로 복사한 자기소개서 작성 안내 텍스트다. 번호, 글자수/"
        "byte 제한, 작성 팁, 무관한 안내 문구가 섞여 있을 수 있다. 여기서 지원자가 실제로 "
        "답변해야 하는 '질문' 또는 '항목'만 뽑아서 정리해라.\n\n"
        f"[원문]\n{raw_text.strip()}\n\n"
        "[규칙]\n"
        "1. 각 항목은 완전한 질문 문장(또는 '~에 대해 서술하시오' 같은 지시문) 하나로 만들어라\n"
        "2. 글자수 제한(예: '500자 이내')은 있으면 질문 끝에 괄호로 짧게 남겨도 좋다\n"
        "3. 번호, 소제목, 안내문구('아래 항목에 답변해주세요' 등)는 제외해라\n"
        "4. 실제로 질문/항목이라고 판단되는 것을 찾지 못하면 빈 배열을 반환해라\n"
        "5. 최대 8개까지만 추린다\n"
        "6. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        '{"questions": ["질문 문장", ...]}'
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
            return CompanyQuestionsResult(ok=False, message=_PARSE_FAIL_MESSAGE)
        questions = as_str_list(data.get("questions"))
        if not questions:
            return CompanyQuestionsResult(
                ok=False, message="입력한 텍스트에서 질문 항목을 찾지 못했습니다. 기본 질문으로 진행해주세요."
            )
        return CompanyQuestionsResult(ok=True, questions=questions[:8])
    except Exception as e:
        return CompanyQuestionsResult(
            ok=False, message=f"양식 분석에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )


def _career_context(job: str, tech_summary: str) -> str:
    lines = [f"[목표 직무] {job or '미지정'}"]
    if tech_summary.strip():
        lines.append(f"[기술/프로젝트 요약] {tech_summary.strip()}")
    return "\n".join(lines) + "\n\n"


def generate_draft(
    job: str = "", tech_summary: str = "", answers: list[str] | None = None, questions: list[str] | None = None
) -> SelfIntroductionDraft:
    """질문 목록(기본값 GUIDED_QUESTIONS, 회사 양식을 파싱했으면 그 커스텀 질문 목록)에
    순서를 맞춘 답변 목록(빈 답변은 건너뜀)을 받아, 자연스러운 자기소개서 문단으로 다듬어
    반환한다. 답변에 없는 내용을 지어내지 말라고 명시적으로 지시한다 - 이건 "AI가 이력서를
    대신 써주는" 기능이 아니라 "사용자가 말한 내용을 정리해주는" 기능이어야 한다(신뢰성 문제
    - 지어낸 경력이 실제 면접에서 들통나면 안 됨).

    2026-08-13: questions 파라미터 추가 - 회사 자소서 양식을 파싱해서 만든 커스텀 질문
    목록으로도 같은 방식으로 초안을 생성할 수 있게 했다(parse_company_questions 참고).
    안 넘기면 기존처럼 GUIDED_QUESTIONS를 쓴다(하위 호환)."""
    answers = answers or []
    effective_questions = questions if questions else list(GUIDED_QUESTIONS)
    if not settings.gemini_api_key:
        return SelfIntroductionDraft(ok=False, message=_NO_KEY_MESSAGE)
    if not any(a.strip() for a in answers):
        return SelfIntroductionDraft(ok=False, message=_NO_ANSWER_MESSAGE)

    qa_blocks = [
        f"[질문] {q}\n[답변] {a.strip()}"
        for q, a in zip(effective_questions, answers)
        if a.strip()
    ]
    qa_text = "\n\n".join(qa_blocks)

    prompt = (
        "당신은 한국 채용 시장에 정통한 이력서 컨설턴트입니다. 아래 지원자 정보와 질문별 "
        "답변을 참고해서, 채용 담당자에게 어필할 수 있는 자기소개서 한 편을 작성해주세요.\n\n"
        f"{_career_context(job, tech_summary)}"
        f"[질문과 답변]\n{qa_text}\n\n"
        "[작성 규칙]\n"
        "1. 답변에 없는 경험이나 사실을 지어내지 마라 - 이 작업은 답변 내용을 자연스러운 "
        "문장으로 다듬고 연결하는 것이지, 새로운 경험이나 성과를 창작하는 게 아니다\n"
        "2. 문어체 존댓말로, 채용 자기소개서에 맞는 격식 있는 톤으로 작성해라\n"
        "3. 답변이 있는 질문마다 문단 하나씩으로 구성하되, 문단 사이 흐름이 자연스럽게 "
        "이어지게 해라 - 질문 번호나 소제목은 붙이지 마라\n"
        "4. 전체 600~1200자 내외로 작성해라\n"
        "5. 마크다운, 글머리 기호, 따옴표 없이 완성된 본문 텍스트만 출력해라 - 설명이나 "
        "다른 말은 절대 붙이지 마라\n"
    )

    try:
        from google import genai

        client = genai.Client(api_key=settings.gemini_api_key)
        response = client.models.generate_content(model=settings.gemini_model, contents=prompt)
        content = (response.text or "").strip()
        if not content:
            return SelfIntroductionDraft(ok=False, message=_PARSE_FAIL_MESSAGE)
        return SelfIntroductionDraft(ok=True, content=content)
    except Exception as e:
        return SelfIntroductionDraft(
            ok=False, message=f"자기소개서 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )


def critique(content: str, job: str = "", tech_summary: str = "") -> SelfIntroductionCritique:
    """이미 작성된 자기소개서 텍스트를 받아 강점/개선점/일부 수정 예시를 JSON으로 받는다.
    evaluation.py의 rubric 패턴(JSON 강제 출력)과 동일하게 구성했다."""
    if not settings.gemini_api_key:
        return SelfIntroductionCritique(ok=False, message=_NO_KEY_MESSAGE)
    if not content.strip():
        return SelfIntroductionCritique(ok=False, message=_NO_CONTENT_MESSAGE)

    prompt = (
        "당신은 한국 채용 시장에 정통한 이력서 컨설턴트입니다. 아래 지원자의 자기소개서를 "
        "읽고 첨삭 의견을 정해진 JSON 형식으로만 응답하세요.\n\n"
        f"{_career_context(job, tech_summary)}"
        f"[자기소개서 원문]\n{content.strip()}\n\n"
        "[작성 규칙]\n"
        "1. strengths(잘 쓴 부분)는 1~3개, improvements(고치면 좋을 부분)는 1~5개, 각각 "
        "짧고 구체적인 한국어 문장으로 작성해라(60자 내외) - '더 좋아질 수 있습니다' 같은 "
        "막연한 말 대신 구체적으로 어디를 어떻게 고치라는 건지 밝혀라\n"
        "2. revised_example은 원문 전체를 다시 쓰는 게 아니라, 가장 개선이 필요한 한두 "
        "문단만 골라 개선 방향을 반영해서 다시 쓴 예시로 작성해라(300자 내외) - 원문에 없는 "
        "경험/사실을 지어내지 마라\n"
        "3. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "strengths": ["문장", ...],\n'
        '  "improvements": ["문장", ...],\n'
        '  "revised_example": "문단"\n'
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
            return SelfIntroductionCritique(ok=False, message=_PARSE_FAIL_MESSAGE)
        return SelfIntroductionCritique(
            ok=True,
            strengths=as_str_list(data.get("strengths")),
            improvements=as_str_list(data.get("improvements")),
            revised_example=(str(data.get("revised_example") or "").strip() or None),
        )
    except Exception as e:
        return SelfIntroductionCritique(
            ok=False, message=f"자기소개서 첨삭에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
