import { Plus, X } from "lucide-react";
import { useEffect, useState } from "react";
import { createSelfIntroduction, deleteSelfIntroduction, listSelfIntroductions, updateSelfIntroduction } from "../api/resumeApi";
import type { SelfIntroduction } from "../model/resume.types";

type Draft = { id?: number; category: string; title: string; content: string };
const categories = ["성장과정 및 성격", "특기 및 잘 할 수 있는 일", "습득기술", "지원동기", "입사 후 포부", "직접 입력"];
const emptyDraft = (): Draft => ({ category: categories[0], title: "", content: "" });

export function SelfIntroductionSection() {
  const [entries, setEntries] = useState<SelfIntroduction[]>([]); const [drafts, setDrafts] = useState<Draft[]>([]); const [busy, setBusy] = useState(false); const [message, setMessage] = useState("");
  useEffect(() => { void listSelfIntroductions().then(setEntries).catch(() => setMessage("자기소개서를 불러오지 못했습니다.")); }, []);
  const add = () => setDrafts((current) => [...current, emptyDraft()]);
  const edit = (entry: SelfIntroduction) => setDrafts((current) => current.some((draft) => draft.id === entry.id) ? current : [...current, { id: entry.id, category: categories.find((category) => entry.title.startsWith(`[${category}]`)) ?? "직접 입력", title: entry.title.replace(/^\[[^\]]+\]\s*/, ""), content: entry.content }]);
  const updateDraft = (index: number, draft: Draft) => setDrafts((current) => current.map((item, itemIndex) => itemIndex === index ? draft : item));
  const save = async (draft: Draft, index: number) => { if (!draft.title.trim() || !draft.content.trim()) return setMessage("제목과 본문을 입력해 주세요."); const input = { ...draft, primary: false, title: `[${draft.category}] ${draft.title.trim()}` }; setBusy(true); try { const saved = draft.id ? await updateSelfIntroduction(draft.id, input) : await createSelfIntroduction(input); setEntries((current) => [saved, ...current.filter((entry) => entry.id !== saved.id)]); setDrafts((current) => current.filter((_, itemIndex) => itemIndex !== index)); setMessage("임시 저장했습니다."); } catch (error) { setMessage(error instanceof Error ? error.message : "저장에 실패했습니다."); } finally { setBusy(false); } };
  const remove = async (id: number) => { if (!confirm("이 자기소개서를 삭제할까요?")) return; setBusy(true); try { await deleteSelfIntroduction(id); setEntries((current) => current.filter((entry) => entry.id !== id)); } catch (error) { setMessage(error instanceof Error ? error.message : "삭제에 실패했습니다."); } finally { setBusy(false); } };
  return <section className="self-introduction-editor"><div className="jobkorea-entry-title"><div><h3>자기소개서</h3><p>카테고리·제목·본문을 문항별로 여러 개 작성해 관리하세요.</p></div></div>{message && <p className="resume-document-message">{message}</p>}
    {entries.map((entry) => { const draftIndex = drafts.findIndex((draft) => draft.id === entry.id); const draft = draftIndex >= 0 ? drafts[draftIndex] : null; return <article className="self-intro-card" key={entry.id}><div className="self-intro-card-heading"><strong>{entry.title}</strong><span>{entry.content.length.toLocaleString()}자</span><button aria-label={`${entry.title} 삭제`} type="button" onClick={() => void remove(entry.id)} disabled={busy}><X size={17} /></button></div>{draft ? <SelfIntroFields draft={draft} setDraft={(next) => updateDraft(draftIndex, next)} busy={busy} onSave={() => void save(draft, draftIndex)} onCancel={() => setDrafts((current) => current.filter((_, index) => index !== draftIndex))} /> : <><textarea rows={8} value={entry.content} readOnly /><div className="form-actions"><button type="button" className="outline-button" onClick={() => edit(entry)}>수정</button></div></>}</article>; })}
    {drafts.map((draft, index) => !draft.id && <article className="self-intro-card editing" key={`new-${index}`}><SelfIntroFields draft={draft} setDraft={(next) => updateDraft(index, next)} busy={busy} onSave={() => void save(draft, index)} onCancel={() => setDrafts((current) => current.filter((_, itemIndex) => itemIndex !== index))} /></article>)}
    <button type="button" className="jobkorea-empty-add" onClick={add}><Plus size={16} /> 자기소개서 추가</button></section>;
}

function SelfIntroFields({ draft, setDraft, busy, onSave, onCancel }: { draft: Draft; setDraft: (draft: Draft) => void; busy: boolean; onSave: () => void; onCancel: () => void }) {
  return <><div className="form-fields"><label>카테고리<select value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })}>{categories.map((category) => <option key={category}>{category}</option>)}</select></label><label>제목*<input value={draft.title} maxLength={120} onChange={(event) => setDraft({ ...draft, title: event.target.value })} placeholder="제목을 입력해 주세요" /></label><label className="wide">본문*<textarea rows={10} value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} placeholder="자기소개서 내용을 입력해 주세요." /></label></div><div className="self-intro-card-footer"><span>{draft.content.length.toLocaleString()}자</span><div className="form-actions"><button className="outline-button" type="button" onClick={onCancel}>취소</button><button className="primary-button" type="button" disabled={busy} onClick={onSave}>임시저장</button></div></div></>;
}
