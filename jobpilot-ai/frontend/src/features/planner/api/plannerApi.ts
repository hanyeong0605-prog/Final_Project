import { getJson } from "../../../api/httpClient";
import { plannerEventsFixture } from "../data/plannerEvents.fixture";
import type { PlannerEvent } from "../model/planner.types";

export function getPlannerEvents(): Promise<PlannerEvent[]> {
  return getJson<PlannerEvent[]>("/api/v1/planner-events?from=2026-08-01&to=2026-08-31", plannerEventsFixture);
}
