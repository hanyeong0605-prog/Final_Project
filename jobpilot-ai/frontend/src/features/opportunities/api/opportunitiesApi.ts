import { getJson } from "../../../api/httpClient";
import type { Opportunity } from "../model/opportunity.types";

export function getRecommendedOpportunities(): Promise<Opportunity[]> {
  return getJson<Opportunity[]>("/api/v1/opportunities/recommended");
}
export function getOpportunity(id: string): Promise<Opportunity> { return getJson<Opportunity>(`/api/v1/opportunities/${id}`); }
