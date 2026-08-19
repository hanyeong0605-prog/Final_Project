import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { searchQnetQualifications, type QnetQualification } from "../api/memberCertificatesApi";
import { CertificateDetailModal } from "./CertificateDetailModal";

// 2026-08-11: showDetail=false면 상세보기 버튼/모달을 아예 안 띄운다 - CareerProfileForm의
// 스펙 입력 칸처럼 "종목만 골라서 바로 추가"가 목적인 곳에서는 상세보기가 불필요한
// 클릭 유도라 뺀다(성장 기회 추천 페이지의 찜하기 모달은 기본값 true로 유지).
type Props = { onSelect: (item: QnetQualification) => void; actionLabel?: string; showDetail?: boolean; onManual?: () => void };

// 2026-08-11: 인라인 드롭다운으로 카드 안 좁은 컬럼에 결과를 욱여넣다 보니 폭 계산이
// 계속 깨져서(선택 버튼 0px로 찌부러지는 버그 등), EducationSearchModal과 똑같은
// "트리거 버튼 → 전체화면 모달" 패턴으로 통째로 옮겼다. 모달은 폭 제약이 없어서
// 같은 종류의 레이아웃 버그가 재발할 여지가 훨씬 적다.
export function CertificateSearchModal({ onSelect, actionLabel = "선택", showDetail = true, onManual }: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<QnetQualification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [detailItem, setDetailItem] = useState<QnetQualification | null>(null);

  useEffect(() => {
    if (!open || query.trim().length < 1) { setResults([]); setError(""); return; }
    const timer = window.setTimeout(() => {
      setLoading(true); setError("");
      void searchQnetQualifications(query.trim())
        .then(setResults)
        .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "검색 결과를 불러오지 못했습니다."))
        .finally(() => setLoading(false));
    }, 300);
    return () => window.clearTimeout(timer);
  }, [open, query]);

  const openModal = () => { setOpen(true); setQuery(""); setResults([]); setError(""); };
  const choose = (item: QnetQualification) => { onSelect(item); setOpen(false); };

  return <>
    <button type="button" className="profile-select-trigger certificate-search-trigger" onClick={openModal}>자격증 종목 찾아보기</button>
    {open && <div className="profile-modal-backdrop" role="presentation" onMouseDown={() => setOpen(false)}>
      <section className="profile-select-modal education-modal" role="dialog" aria-modal="true" aria-labelledby="certificate-search-title" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div><span className="eyebrow">Q-NET 국가자격</span><h3 id="certificate-search-title">자격증 종목 검색</h3><p>보유한 국가자격 종목을 검색해서 선택합니다.</p></div>
          <button type="button" className="modal-close" onClick={() => setOpen(false)} aria-label="닫기"><X size={18} /></button>
        </header>
        <div className="education-search-input"><Search size={17} /><input autoFocus value={query} maxLength={60} onKeyDown={(event) => { if (event.key === "Enter") event.preventDefault(); }} onChange={(event) => setQuery(event.target.value)} placeholder="예: 정보처리기사" />{loading && <small>검색 중</small>}</div>
        {error && <p className="education-search-error">{error}</p>}
        <div className="certificate-modal-results">
          {results.map((item) => <div className="certificate-modal-result-row" key={item.code}>
            <button type="button" onClick={() => choose(item)}><strong>{item.name}</strong><span>{[item.qualificationType, item.field, item.subField].filter(Boolean).join(" · ")}</span></button>
            <button type="button" className="certificate-modal-select" onClick={() => choose(item)}>{actionLabel}</button>
            {showDetail && <button type="button" className="certificate-modal-detail" onClick={() => setDetailItem(item)}>상세보기</button>}
          </div>)}
          {!loading && query.trim().length > 0 && results.length === 0 && !error && <div className="education-search-hint"><p>검색 결과가 없습니다.</p>{onManual && <button type="button" className="outline-button" onClick={() => { onManual(); setOpen(false); }}>직접 입력하기</button>}</div>}
        </div>
      </section>
    </div>}
    {showDetail && detailItem && <CertificateDetailModal item={detailItem} onClose={() => setDetailItem(null)} />}
  </>;
}
