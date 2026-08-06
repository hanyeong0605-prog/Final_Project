import { Check, RotateCcw, X } from "lucide-react";
import { useEffect, useState } from "react";
import { preferredRegions } from "../data/profileCatalog";

export function RegionSelectionModal({ value, onChange }: { value: string[]; onChange: (next: string[]) => void }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>(value);
  useEffect(() => { if (open) setSelected(value); }, [open, value]);
  const toggle = (region: string) => setSelected((current) => current.includes(region) ? current.filter((item) => item !== region) : [...current, region]);
  const summary = value.length === 0 ? "희망 근무지역 선택" : value.length <= 3 ? value.join(", ") : `${value.slice(0, 2).join(", ")} 외 ${value.length - 2}곳`;

  return <>
    <button type="button" className="profile-select-trigger" onClick={() => setOpen(true)}>{summary}</button>
    {open && <div className="profile-modal-backdrop" role="presentation" onMouseDown={() => setOpen(false)}><section className="profile-select-modal" role="dialog" aria-modal="true" aria-labelledby="region-modal-title" onMouseDown={(event) => event.stopPropagation()}>
      <header><div><span className="eyebrow">MULTI SELECT</span><h3 id="region-modal-title">희망 근무지역 선택</h3><p>여러 지역을 선택할 수 있습니다.</p></div><button type="button" className="modal-close" onClick={() => setOpen(false)} aria-label="닫기"><X size={18} /></button></header>
      <div className="region-actions"><button type="button" onClick={() => setSelected([...preferredRegions])}>전체 선택</button><button type="button" onClick={() => setSelected([])}><RotateCcw size={13} />초기화</button></div>
      <div className="region-grid">{preferredRegions.map((region) => <button type="button" className={selected.includes(region) ? "region-option selected" : "region-option"} key={region} onClick={() => toggle(region)}>{selected.includes(region) && <Check size={14} />}{region}</button>)}</div>
      <footer><span>{selected.length}개 선택</span><button type="button" className="primary-button" onClick={() => { onChange(selected); setOpen(false); }}>선택 완료</button></footer>
    </section></div>}
  </>;
}
