import { postJson } from "../../../api/httpClient";

export async function toggleInterest(targetId: number, interested: boolean): Promise<void> {
  await postJson("/api/v1/interests", { targetId, interested }, undefined);
}
