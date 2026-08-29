import { getJson } from "../../../api/httpClient";
import type { CompanyFinanceAnalysis } from "../model/companyFinance.types";

export function getCompanyFinance(jobPostingId: number, signal?: AbortSignal) {
  return getJson<CompanyFinanceAnalysis>(`/api/v1/job-postings/${jobPostingId}/company-finance`, { signal });
}
