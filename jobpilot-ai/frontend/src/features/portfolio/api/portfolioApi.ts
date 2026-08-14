import { getAccessToken, getJson, postJson } from "../../../api/httpClient";
import type { ProjectAnalysis } from "../../project-analysis/model/projectAnalysis.types";
import type { PortfolioDocumentSummary, PortfolioTemplate } from "../model/portfolio.types";

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

export function generatePortfolio(
  analysis: ProjectAnalysis,
  selectedImplementationIds: string[],
  template: PortfolioTemplate,
): Promise<PortfolioDocumentSummary> {
  return postJson<PortfolioDocumentSummary>("/api/v1/project-analysis/portfolio", {
    analysis,
    selectedImplementationIds,
    template,
  });
}

export function listPortfolioDocuments(): Promise<PortfolioDocumentSummary[]> {
  return getJson<PortfolioDocumentSummary[]>("/api/v1/members/me/portfolio-documents");
}

// 다운로드 엔드포인트는 로그인 회원만 접근 가능해서 <a href>로 바로 열 수 없다(Authorization
// 헤더가 안 붙는다) - fetch로 직접 받아 Blob URL을 만들고 클릭을 흉내내는 방식으로 내려받는다.
async function downloadFile(path: string, fallbackFilename: string): Promise<void> {
  const token = getAccessToken();
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBaseUrl}${path}`, { headers });
  if (!response.ok) throw new Error(`파일을 내려받지 못했습니다. (HTTP ${response.status})`);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : fallbackFilename;
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function downloadPortfolioPptx(id: number): Promise<void> {
  return downloadFile(`/api/v1/members/me/portfolio-documents/${id}/pptx`, "portfolio.pptx");
}

export function downloadPortfolioPdf(id: number): Promise<void> {
  return downloadFile(`/api/v1/members/me/portfolio-documents/${id}/pdf`, "portfolio.pdf");
}
