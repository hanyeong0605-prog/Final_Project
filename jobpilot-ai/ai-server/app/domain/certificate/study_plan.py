"""성장 기회 추천 페이지 - 추천 자격증 맞춤 학습 계획 (2026-08-11).

timeline/insight.py와 완전히 같은 패턴(google-genai, JSON 강제 출력, resume._shared 헬퍼
재사용, fail-open dataclass)을 따른다 - 별도로 새 유틸을 만들지 않고 그대로 재사용한다.

"우리가 부족한 점 같은 거 맞춤으로" 요청에 맞춰, 지원자가 이미 보유한 기술스택/자격증을
프롬프트에 같이 넣어서 "이미 아는 내용은 간단히, 새로 배워야 할 부분은 구체적으로"
짜도록 유도한다. 이 모듈은 저장하지 않고 매번 실시간 생성만 한다(#69와 동일 원칙).

주의: 특정 교재/강의/사이트명을 지어내면 실재하지 않는 자료를 추천하는 환각 위험이 있어서,
프롬프트에서 구체적 상품/브랜드명 대신 학습 "방법론"만 제안하도록 명시했다.
"""

from dataclasses import dataclass, field

from app.core.config import settings
from app.domain.resume._shared import as_str_list, parse_json_response

_NO_KEY_MESSAGE = "학습 계획을 보려면 GEMINI_API_KEY 설정이 필요합니다."
_NO_CERTIFICATE_MESSAGE = "자격증 정보가 없습니다."
_PARSE_FAIL_MESSAGE = "학습 계획을 정리하지 못했어요. 잠시 후 다시 시도해 주세요."


@dataclass
class CertificateStudyPlan:
    ok: bool
    message: str | None = None
    study_weeks: int | None = None
    focus_areas: list[str] = field(default_factory=list)
    weekly_plan: list[str] = field(default_factory=list)
    study_tips: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "ok": self.ok,
            "message": self.message,
            "study_weeks": self.study_weeks,
            "focus_areas": self.focus_areas,
            "weekly_plan": self.weekly_plan,
            "study_tips": self.study_tips,
        }


def _clamp_weeks(value: object) -> int | None:
    try:
        weeks = int(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None
    return max(1, min(weeks, 24))


def generate_study_plan(
    certificate_name: str,
    qualification_type: str = "",
    field_name: str = "",
    sub_field: str = "",
    target_job_family: str = "",
    target_role: str = "",
    skills: list[str] | None = None,
    owned_certificates: list[str] | None = None,
) -> CertificateStudyPlan:
    skills = skills or []
    owned_certificates = owned_certificates or []

    if not settings.gemini_api_key:
        return CertificateStudyPlan(ok=False, message=_NO_KEY_MESSAGE)
    if not certificate_name.strip():
        return CertificateStudyPlan(ok=False, message=_NO_CERTIFICATE_MESSAGE)

    cert_context = certificate_name.strip()
    cert_meta = " · ".join(p for p in (qualification_type, field_name, sub_field) if p.strip())
    if cert_meta:
        cert_context += f" ({cert_meta})"

    profile_lines = []
    if target_job_family.strip() or target_role.strip():
        profile_lines.append(
            "목표 직무: " + " / ".join(p for p in (target_job_family.strip(), target_role.strip()) if p)
        )
    profile_lines.append("보유 기술스택: " + (", ".join(skills[:15]) if skills else "(등록 없음)"))
    profile_lines.append("보유 자격증: " + (", ".join(owned_certificates[:10]) if owned_certificates else "(등록 없음)"))
    profile_text = "\n".join(profile_lines)

    prompt = (
        "당신은 한국 국가기술자격 시험을 준비하는 취업 준비생을 위한 학습 코치입니다. "
        f"아래 지원자가 '{cert_context}' 자격증 취득을 준비하고 있습니다.\n\n"
        f"[지원자 정보]\n{profile_text}\n\n"
        "[작성 규칙]\n"
        "1. study_weeks: 이 지원자 기준으로 합리적인 예상 학습 기간을 1~24 사이 정수(주 단위)로 추정해라. "
        "보유 기술/자격증이 해당 분야와 겹치면 더 짧게 잡아라\n"
        "2. focus_areas: 이 지원자가 우선적으로 학습해야 할 영역을 2~5개 뽑아라 - 이미 보유한 "
        "기술/자격증과 겹치는 부분은 제외하거나 간단히만 언급하고, 부족할 만한 부분을 구체적으로 짚어라 "
        "(각 항목 40자 내외)\n"
        "3. weekly_plan: study_weeks를 몇 개 구간으로 나눠 각 구간에 무엇을 할지 문장으로 써라 "
        "(예: '1~2주차: ...'), 최대 5개 항목\n"
        "4. study_tips: 실전 팁을 2~4개 제안해라 - 특정 책/강의/사이트 이름은 절대 지어내지 말고, "
        "학습 방법론(예: 기출문제 반복, 오답노트 등)만 제안해라(각 항목 40자 내외)\n"
        "5. 아래 스키마의 JSON 객체 하나만 출력해라 - 설명, 마크다운, 코드펜스 없이:\n"
        "{\n"
        '  "study_weeks": 정수,\n'
        '  "focus_areas": ["문장", ...],\n'
        '  "weekly_plan": ["문장", ...],\n'
        '  "study_tips": ["문장", ...]\n'
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
            return CertificateStudyPlan(ok=False, message=_PARSE_FAIL_MESSAGE)
        return CertificateStudyPlan(
            ok=True,
            study_weeks=_clamp_weeks(data.get("study_weeks")),
            focus_areas=as_str_list(data.get("focus_areas")),
            weekly_plan=as_str_list(data.get("weekly_plan")),
            study_tips=as_str_list(data.get("study_tips")),
        )
    except Exception as e:
        return CertificateStudyPlan(
            ok=False, message=f"학습 계획 생성에 실패했습니다 ({type(e).__name__}). 잠시 후 다시 시도해 주세요."
        )
