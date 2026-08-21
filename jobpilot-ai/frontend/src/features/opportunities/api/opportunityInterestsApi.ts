import { getJson, postJson } from "../../../api/httpClient";
import type { Opportunity } from "../model/opportunity.types";
const type = "OPPORTUNITY";
export const getOpportunityInterestIds = () => getJson<number[]>(`/api/v1/interests?targetType=${type}`);
export const toggleOpportunityInterest = (targetId: number, interested: boolean) => postJson("/api/v1/interests", { targetType: type, targetId, interested });
export const getBookmarkedOpportunities = () => getJson<Opportunity[]>("/api/v1/opportunities/bookmarked");
