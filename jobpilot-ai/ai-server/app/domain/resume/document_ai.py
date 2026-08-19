"""AI helpers for resume profile extraction and editable resume drafts."""

from dataclasses import dataclass, field

from app.core.config import settings
from app.domain.resume._shared import as_str_list, parse_json_response


@dataclass
class ResumeProfileAnalysis:
    ok: bool
    message: str | None = None
    profile: dict[str, object] = field(default_factory=dict)

    def to_dict(self) -> dict[str, object]:
        return {"ok": self.ok, "message": self.message, "profile": self.profile}


@dataclass
class ResumeDocumentDraft:
    ok: bool
    message: str | None = None
    content: str | None = None

    def to_dict(self) -> dict[str, object]:
        return {"ok": self.ok, "message": self.message, "content": self.content}


def analyze_profile(text: str) -> ResumeProfileAnalysis:
    if not settings.gemini_api_key:
        return ResumeProfileAnalysis(ok=False, message="GEMINI_API_KEY가 설정되지 않았습니다.")
    if not text.strip():
        return ResumeProfileAnalysis(ok=False, message="분석할 이력서 텍스트가 없습니다.")

    prompt = (
        "당신은 한국 이력서에서 사실만 구조화해 추출하는 도우미입니다. 아래 이력서 원문에 "
        "명시된 정보만 JSON으로 추출하세요. 추측하거나 없는 경력·자격증·기술을 만들지 마세요.\n\n"
        f"[이력서 원문]\n{text.strip()[:14000]}\n\n"
        "[규칙]\n"
        "- targetRole: 명시된 희망/지원 직무, 없으면 빈 문자열\n"
        "- suggestedSkills: 실제 사용·보유 기술만 최대 30개\n"
        "- suggestedCertificates: 취득한 자격증만\n"
        "- educationLevel: HIGH_SCHOOL, ASSOCIATE, BACHELOR, MASTER, DOCTOR 중 하나 또는 빈 문자열\n"
        "- major: 전공명만, 없으면 빈 문자열\n"
        "- totalCareerMonths: 명시된 경력 기간의 합산 개월 수, 계산 불가하면 0\n"
        "- technicalSummary: 원문에 있는 사실만 2문장 이내로 요약, 없으면 빈 문자열\n"
        "아래 JSON 객체 하나만 반환하세요.\n"
        '{"targetRole":"","suggestedSkills":[],"suggestedCertificates":[],"educationLevel":"","major":"","totalCareerMonths":0,"technicalSummary":""}'
    )
    try:
        from google import genai
        from google.genai import types

        response = genai.Client(api_key=settings.gemini_api_key).models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type="application/json"),
        )
        data = parse_json_response(response.text or "")
        if not data:
            return ResumeProfileAnalysis(ok=False, message="AI 응답을 해석하지 못했습니다.")
        profile = {
            "targetRole": str(data.get("targetRole") or "").strip(),
            "suggestedSkills": as_str_list(data.get("suggestedSkills"))[:30],
            "suggestedCertificates": as_str_list(data.get("suggestedCertificates"))[:20],
            "educationLevel": str(data.get("educationLevel") or "").strip().upper(),
            "major": str(data.get("major") or "").strip(),
            "totalCareerMonths": max(0, int(data.get("totalCareerMonths") or 0)),
            "technicalSummary": str(data.get("technicalSummary") or "").strip(),
        }
        return ResumeProfileAnalysis(ok=True, profile=profile)
    except Exception as error:
        return ResumeProfileAnalysis(ok=False, message=f"AI 이력서 분석에 실패했습니다 ({type(error).__name__}).")


def generate_document_draft(profile: dict[str, object], answers: list[str], template_key: str, template_hint: str = "") -> ResumeDocumentDraft:
    if not settings.gemini_api_key:
        return ResumeDocumentDraft(ok=False, message="GEMINI_API_KEY가 설정되지 않았습니다.")
    if not any(value.strip() for value in answers):
        return ResumeDocumentDraft(ok=False, message="이력서 작성을 위한 답변을 하나 이상 입력해주세요.")

    labels = ["성장과정 및 성격", "내가 잘할 수 있는 일", "습득기술 및 직무관련 역량", "회사 업무에 대한 자세 및 포부"]
    qa = "\n\n".join(f"[{labels[index]}]\n{value.strip()}" for index, value in enumerate(answers[:4]) if value.strip())
    prompt = (
        "당신은 한국 채용용 이력서 초안을 작성하는 전문가입니다. 아래 프로필과 답변을 토대로 "
        "수정 가능한 Word 문서에 들어갈 이력서 초안을 한국어로 작성하세요.\n\n"
        f"[저장된 역량]\n{profile}\n\n[질문 답변]\n{qa}\n\n"
        f"[선택 양식] {template_key}\n[첨부 양식의 항목 힌트]\n{template_hint[:3000]}\n\n"
        "[규칙]\n"
        "1. 입력된 프로필·답변에 없는 사실, 수치, 회사명, 프로젝트 성과를 만들지 마세요.\n"
        "2. 다음 제목을 사용하세요: 지원 직무, 핵심 역량, 학력 및 경력, 프로젝트 및 경험, 자기소개. detailedEntries가 있으면 해당 유형(학력/경력/활동/수상/어학/포트폴리오)의 사실도 맞는 제목 아래 반영하세요.\n"
        "3. 양식 힌트가 있으면 항목 순서를 참고하되 원본 디자인을 복제하려 하지 마세요.\n"
        "4. 각 항목은 수정 가능한 평문으로, 마크다운 표 없이 작성하세요.\n"
        "5. 정보가 없는 항목에는 '[직접 입력 필요]'라고 적으세요.\n"
    )
    try:
        from google import genai

        response = genai.Client(api_key=settings.gemini_api_key).models.generate_content(
            model=settings.gemini_model, contents=prompt
        )
        content = (response.text or "").strip()
        if not content:
            return ResumeDocumentDraft(ok=False, message="AI가 이력서 초안을 만들지 못했습니다.")
        return ResumeDocumentDraft(ok=True, content=content)
    except Exception as error:
        return ResumeDocumentDraft(ok=False, message=f"AI 이력서 초안 생성에 실패했습니다 ({type(error).__name__}).")
