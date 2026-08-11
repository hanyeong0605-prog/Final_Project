import { useState } from "react";
import { checkCertificateAuthenticity } from "../api/memberCertificatesApi";

// 2026-08-11: 자격증 카드 안에서 발급번호만 입력하면 KCA 진위여부 API로 확인해주는
// 작은 인라인 위젯. 별도 컴포넌트로 뺀 이유는 CareerProfileForm.tsx가 이미 상태가
// 많아서(certificates 배열 자체를 map으로 렌더링) 항목별 로컬 상태(입력값/로딩/결과)를
// 배열 인덱스로 관리하면 더 복잡해지기 때문 - 여긴 자체 useState로 완결됨.
export function CertificateAuthenticityCheck() {
  const [open, setOpen] = useState(false);
  const [number, setNumber] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<boolean | null>(null);
  const [error, setError] = useState("");

  const check = async () => {
    const trimmed = number.trim();
    if (!trimmed) return;
    setLoading(true); setError(""); setResult(null);
    try { setResult(await checkCertificateAuthenticity(trimmed)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "진위확인에 실패했습니다."); }
    finally { setLoading(false); }
  };

  if (!open) {
    return <button type="button" className="certificate-authenticity-trigger" onClick={() => setOpen(true)}>진위확인</button>;
  }

  return <div className="certificate-authenticity-check">
    <div className="certificate-authenticity-row">
      <input
        value={number}
        maxLength={30}
        onChange={(event) => { setNumber(event.target.value); setResult(null); setError(""); }}
        onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); void check(); } }}
        placeholder="자격증 발급번호 (예: 1230001K018P)"
      />
      <button type="button" disabled={loading || !number.trim()} onClick={() => void check()}>{loading ? "확인 중" : "확인"}</button>
      <button type="button" className="certificate-authenticity-close" onClick={() => setOpen(false)} aria-label="진위확인 닫기">×</button>
    </div>
    {result === true && <p className="certificate-authenticity-result ok">정상 발급된 자격증으로 확인됐습니다.</p>}
    {result === false && <p className="certificate-authenticity-result fail">일치하는 자격증 정보를 찾지 못했습니다. 발급번호를 다시 확인해 주세요.</p>}
    {error && <p className="education-search-error">{error}</p>}
    <p className="form-hint">무선설비·통신설비·전파전자·정보통신 분야 국가기술자격증(개인)만 확인 가능합니다.</p>
  </div>;
}
