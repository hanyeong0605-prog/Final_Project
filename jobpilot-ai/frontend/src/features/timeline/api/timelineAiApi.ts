// resumeAiApi.ts와 같은 이유로 Spring이 아니라 ai-server로 직접 보낸다(/ai-api 프록시).

export interface TimelineInsightResult {
  ok: boolean;
  message: string | null;
  recurring_points: string[];
  resume_linked_suggestion: string | null;
}

// InterviewSessionRecordSummary(목록 응답)엔 improvements가 없다(가벼운 요약만 담는 설계 -
// timeline.types.ts 참고) - 인사이트는 "반복되는 개선점"이 핵심이라 improvements가 꼭
// 있어야 해서, 호출하는 쪽(TimelinePage)이 최근 세션 몇 개는 상세 조회까지 해서 이 모양으로
// 넘겨준다.
export interface InsightSessionInput {
  role: string | null;
  interviewType: string | null;
  overallScore: number | null;
  improvements: string[];
}

interface ProjectContentInput {
  title: string;
  roleDescription: string | null;
  problemDescription: string | null;
  solutionDescription: string | null;
  resultDescription: string | null;
}

export async function generateTimelineInsight(
  sessions: InsightSessionInput[],
  selfIntroductions: string[],
  projects: ProjectContentInput[],
): Promise<TimelineInsightResult> {
  const response = await fetch("/ai-api/timeline/insight/generate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      sessions: sessions.map((s) => ({
        role: s.role ?? "",
        interview_type: s.interviewType ?? "",
        overall_score: s.overallScore,
        improvements: s.improvements,
      })),
      self_introductions: selfIntroductions,
      projects: projects.map((p) => ({
        title: p.title,
        role_description: p.roleDescription ?? "",
        problem_description: p.problemDescription ?? "",
        solution_description: p.solutionDescription ?? "",
        result_description: p.resultDescription ?? "",
      })),
    }),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(error?.detail ?? `요청 실패 (HTTP ${response.status})`);
  }
  return response.json();
}
