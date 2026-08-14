import { useEffect, useState } from "react";
import { Briefcase, FileText, Presentation } from "lucide-react";
import { downloadPortfolioPdf, downloadPortfolioPptx, listPortfolioDocuments } from "../api/portfolioApi";
import type { PortfolioDocumentSummary } from "../model/portfolio.types";

// 마이페이지에서 GitHub 코드 분석 화면에서 만든 포트폴리오를 다시 내려받는 섹션.
// SubscriptionSection과 같은 패턴: 마운트 시 자기 데이터를 스스로 불러온다.
export function PortfolioSection() {
  const [documents, setDocuments] = useState<PortfolioDocumentSummary[]>([]);
  const [error, setError] = useState("");
  const [busyKey, setBusyKey] = useState<string | null>(null);

  useEffect(() => {
    void listPortfolioDocuments().then(setDocuments).catch(() => setDocuments([]));
  }, []);

  const handleDownload = async (id: number, format: "pptx" | "pdf") => {
    const key = `${id}-${format}`;
    setBusyKey(key);
    setError("");
    try {
      await (format === "pptx" ? downloadPortfolioPptx(id) : downloadPortfolioPdf(id));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "파일을 내려받지 못했습니다.");
    } finally {
      setBusyKey(null);
    }
  };

  return (
    <>
      <div className="mypage-section-title">
        <div>
          <h2>내 포트폴리오</h2>
          <p>GitHub 코드 분석에서 만든 발표 자료를 다시 내려받습니다.</p>
        </div>
      </div>
      {error && <div className="account-alert error">{error}</div>}
      {documents.length === 0 ? (
        <section className="panel saved-empty">
          아직 만든 포트폴리오가 없습니다. GitHub 코드 분석 화면에서 만들어 보세요.
        </section>
      ) : (
        <section className="portfolio-doc-list">
          {documents.map((doc) => (
            <article className="portfolio-doc-card" key={doc.id}>
              <Briefcase size={18} />
              <div>
                <strong>{doc.title}</strong>
                <small>{doc.repositoryFullName} · {new Date(doc.createdAt).toLocaleDateString("ko-KR")}</small>
              </div>
              <div className="portfolio-doc-actions">
                {doc.hasPptx && (
                  <button
                    className="outline-button"
                    disabled={busyKey === `${doc.id}-pptx`}
                    onClick={() => void handleDownload(doc.id, "pptx")}
                    type="button"
                  >
                    <Presentation size={14} /> PPTX
                  </button>
                )}
                {doc.hasPdf && (
                  <button
                    className="outline-button"
                    disabled={busyKey === `${doc.id}-pdf`}
                    onClick={() => void handleDownload(doc.id, "pdf")}
                    type="button"
                  >
                    <FileText size={14} /> PDF
                  </button>
                )}
              </div>
            </article>
          ))}
        </section>
      )}
    </>
  );
}
