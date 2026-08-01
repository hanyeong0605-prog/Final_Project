import { deleteJson, getJson, postJson, putJson } from "../../../api/httpClient";
import type { PlannerEvent, PlannerEventInput } from "../model/planner.types";

export function getPlannerEvents(now = new Date()): Promise<PlannerEvent[]> {
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  const format = (date: Date) => date.toLocaleDateString("en-CA");
  return getJson<PlannerEvent[]>(`/api/v1/planner-events?from=${format(firstDay)}&to=${format(lastDay)}`);
}

export const createPlannerEvent = (input: PlannerEventInput) => postJson<PlannerEvent>("/api/v1/planner-events", input);
export const updatePlannerEvent = (id: number, input: PlannerEventInput) => putJson<PlannerEvent>(`/api/v1/planner-events/${id}`, input);
export const deletePlannerEvent = (id: number) => deleteJson(`/api/v1/planner-events/${id}`);
