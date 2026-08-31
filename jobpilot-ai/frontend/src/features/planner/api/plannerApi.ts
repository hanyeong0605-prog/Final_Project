import { deleteJson, getJson, postJson, putJson } from "../../../api/httpClient";
import type { PlannerEvent, PlannerEventInput } from "../model/planner.types";

const toLocalIsoDate = (date: Date) => [
  date.getFullYear(),
  String(date.getMonth() + 1).padStart(2, "0"),
  String(date.getDate()).padStart(2, "0"),
].join("-");

export function getPlannerEvents(now = new Date()): Promise<PlannerEvent[]> {
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return getJson<PlannerEvent[]>(`/api/v1/planner-events?from=${toLocalIsoDate(firstDay)}&to=${toLocalIsoDate(lastDay)}`);
}

export const createPlannerEvent = (input: PlannerEventInput) => postJson<PlannerEvent>("/api/v1/planner-events", input);
export const updatePlannerEvent = (id: number, input: PlannerEventInput) => putJson<PlannerEvent>(`/api/v1/planner-events/${id}`, input);
export const deletePlannerEvent = (id: number) => deleteJson(`/api/v1/planner-events/${id}`);
