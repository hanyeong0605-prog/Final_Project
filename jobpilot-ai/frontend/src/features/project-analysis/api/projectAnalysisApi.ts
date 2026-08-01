import { postJson } from "../../../api/httpClient";
import type { ProjectAnalysis } from "../model/projectAnalysis.types";

export async function analyzeGitHubRepository(repositoryUrl: string): Promise<ProjectAnalysis | null> {
  return postJson<ProjectAnalysis | null>(
    "/api/v1/project-analysis/github",
    { repositoryUrl },
  );
}
