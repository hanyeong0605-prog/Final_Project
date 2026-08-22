import { getJson } from "../../../api/httpClient";

export type RecommendedBook = { isbn13: string; title: string; author: string; publisher: string; publishedAt: string; coverUrl: string; link: string; description: string; category: string; price: number; rating: number; tags: string[]; };
export type RecommendedBookPage = { items: RecommendedBook[]; hasMore: boolean; total: number; recommendationKeyword: string; evidence: string; };

export function listRecommendedBooks(options: { jobPostingId?: string | null; requirementId?: string | null; query?: string; category?: string; sort?: string; page?: number; size?: number }): Promise<RecommendedBookPage> {
  const params = new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 30), category: options.category ?? "ALL", sort: options.sort ?? "relevance" });
  if (options.jobPostingId) params.set("jobPostingId", options.jobPostingId);
  if (options.requirementId) params.set("requirementId", options.requirementId);
  if (options.query?.trim()) params.set("query", options.query.trim());
  return getJson<RecommendedBookPage>(`/api/v1/books?${params.toString()}`);
}
