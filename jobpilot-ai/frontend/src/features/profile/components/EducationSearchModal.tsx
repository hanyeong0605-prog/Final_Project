import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { searchEducationMajors, searchEducationSchools, type EducationMajor, type EducationSchool } from "../api/educationLookupApi";

type Kind = "school" | "major";
type Props = { kind: Kind; educationLevel: string | null; selectedSchool: string | null; value: string | null; onSelect: (value: string) => void };

export function EducationSearchModal({ kind, educationLevel, selectedSchool, value, onSelect }: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<(EducationSchool | EducationMajor)[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const isSchool = kind === "school";
  const label = isSchool ? "학교명" : "전공";
  const placeholder = isSchool ? "학교명 검색" : "전공명 검색";
  const blocked = !educationLevel;

  useEffect(() => {
    if (!open || query.trim().length < 2) { setResults([]); return; }
    const timer = window.setTimeout(() => {
      setLoading(true); setError("");
      const request = isSchool ? searchEducationSchools(query.trim(), educationLevel) : searchEducationMajors(query.trim(), educationLevel, selectedSchool ?? "");
      void request.then(setResults).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "검색 결과를 불러오지 못했습니다.")).finally(() => setLoading(false));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [educationLevel, isSchool, open, query]);

  const openModal = () => { if (blocked) return; setOpen(true); setQuery(""); setResults([]); setError(""); };
  const choose = (item: EducationSchool | EducationMajor) => { onSelect(item.name); setOpen(false); };

  return <>
    <button type="button" className="profile-select-trigger education-trigger" disabled={blocked} onClick={openModal}>{value || (blocked ? (!educationLevel ? "최종 학력을 먼저 선택해 주세요" : "학교를 먼저 선택해 주세요") : `${label} 검색·선택`)}</button>
    {open && <div className="profile-modal-backdrop" role="presentation" onMouseDown={() => setOpen(false)}><section className="profile-select-modal education-modal" role="dialog" aria-modal="true" aria-labelledby={`${kind}-modal-title`} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><span className="eyebrow">CAREERNET OPEN API</span><h3 id={`${kind}-modal-title`}>{label} 검색</h3><p>{isSchool ? "커리어넷 학교정보에서 검색 후 선택합니다." : `${selectedSchool}에 실제 개설된 전공만 커리어넷 학과사전에서 확인합니다.`}</p></div><button type="button" className="modal-close" onClick={() => setOpen(false)} aria-label="닫기"><X size={18} /></button></header>
      <div className="education-search-input"><Search size={17} /><input autoFocus value={query} maxLength={60} onKeyDown={(event) => { if (event.key === "Enter") event.preventDefault(); }} onChange={(event) => setQuery(event.target.value)} placeholder={isSchool ? "예: 한국대학교, ICT고등학교" : "예: 컴퓨터공학, 경영학"} />{loading && <small>검색 중</small>}</div>
      <p className="education-search-hint">두 글자 이상 입력하면 검색합니다.</p>
      {error && <p className="education-search-error">{error}</p>}
      <div className="education-results">{results.map((item) => <button type="button" key={`${item.id}-${item.name}`} onClick={() => choose(item)}><strong>{item.name}</strong>{isSchool ? <span>{(item as EducationSchool).schoolType} · {(item as EducationSchool).region}{(item as EducationSchool).campusName ? ` · ${(item as EducationSchool).campusName}` : ""}</span> : <span>{(item as EducationMajor).field}{(item as EducationMajor).relatedNames ? ` · ${(item as EducationMajor).relatedNames}` : ""}</span>}</button>)}</div>
    </section></div>}
  </>;
}
