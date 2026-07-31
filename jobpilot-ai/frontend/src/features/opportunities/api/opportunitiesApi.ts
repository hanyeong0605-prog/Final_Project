import { getJson } from "../../../api/httpClient";
import { opportunitiesFixture } from "../data/opportunities.fixture";
import type { Opportunity } from "../model/opportunity.types";

export function getRecommendedOpportunities(): Promise<Opportunity[]> {
  return getJson<Opportunity[]>("/api/v1/opportunities/recommended", opportunitiesFixture);
}
