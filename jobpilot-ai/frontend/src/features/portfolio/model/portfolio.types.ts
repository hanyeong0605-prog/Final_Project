export type PortfolioTemplate = "LIGHT" | "DARK" | "BRAND_BLUE";

export interface PortfolioDocumentSummary {
  id: number;
  repositoryFullName: string;
  repositoryUrl: string;
  title: string;
  narrativeSource: "GEMINI" | "STATIC_FALLBACK" | string;
  template: PortfolioTemplate | string;
  hasPptx: boolean;
  hasPdf: boolean;
  createdAt: string;
}
