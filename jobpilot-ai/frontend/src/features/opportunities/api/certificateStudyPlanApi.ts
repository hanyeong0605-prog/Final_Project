// timelineAiApi.ts와 같은 이유로 Spring이 아니라 ai-server로 직접 보낸다(/ai-api 프록시).
// 2026-08-11: 추천 자격증 카드의 "AI 학습 계획" 버튼에서 호출한다 - 저장은 안 하고 매번
// 실시간 생성(#69 인사이트와 동일 원칙).

export interface CertificateStudyPlanResult {
  ok: boolean;
  message: string | null;
  study_weeks: number | null;
  focus_areas: string[];
  weekly_plan: string[];
  study_tips: string[];
}

export interface StudyPlanProfileInput {
  targetJobFamily: string;
  targetRole: string;
  skills: string[];
  ownedCertificates: string[];
}

export async function generateCertificateStudyPlan(
  certificateName: string,
  qualificationType: string,
  fieldName: string,
  subField: string,
  profile: StudyPlanProfileInput,
): Promise<CertificateStudyPlanResult> {
  const response = await fetch("/ai-api/certificates/study-plan/generate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      certificate_name: certificateName,
      qualification_type: qualificationType,
      field_name: fieldName,
      sub_field: subField,
      target_job_family: profile.targetJobFamily,
      target_role: profile.targetRole,
      skills: profile.skills,
      owned_certificates: profile.ownedCertificates,
    }),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(error?.detail ?? `요청 실패 (HTTP ${response.status})`);
  }
  return response.json();
}
