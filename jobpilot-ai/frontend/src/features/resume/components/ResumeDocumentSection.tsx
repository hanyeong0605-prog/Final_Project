import { useEffect, useRef, useState } from "react";
import { Download, FileText, Sparkles, Upload } from "lucide-react";
import { applyResumeExtraction, extractResumeDocument, generateResumeDocument, listResumeDocuments, type ResumeDocument } from "../api/resumeApi";

export function ResumeDocumentSection() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [documents, setDocuments] = useState<ResumeDocument[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [request, setRequest] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const load = () => void listResumeDocuments().then(setDocuments).catch(() => setMessage("저장된 이력서 목록을 불러오지 못했습니다."));
  useEffect(load, []);
  const upload = async () => {
    if (!file) { setMessage("PDF 또는 DOCX 이력서를 선택해 주세요."); return; }
    setLoading(true); setMessage("");
    try { const saved = await extractResumeDocument(file); setDocuments((values) => [saved, ...values]); setFile(null); if (inputRef.current) inputRef.current.value = ""; setMessage("추출을 완료했습니다. 아래 제안 정보를 검토한 뒤 프로필에 반영하세요."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "이력서 텍스트를 읽지 못했습니다."); } finally { setLoading(false); }
  };
  const apply = async (id: number) => { setLoading(true); try { await applyResumeExtraction(id); setMessage("역량 프로필에 반영하고 공고 매칭을 다시 계산하도록 요청했습니다."); } catch (error) { setMessage(error instanceof Error ? error.message : "프로필 반영에 실패했습니다."); } finally { setLoading(false); } };
  const generate = async () => { setLoading(true); try { const saved = await generateResumeDocument({ title: "Job-A-Dream 이력서 초안", additionalRequest: request }); setDocuments((values) => [saved, ...values]); setMessage("수정 가능한 이력서 초안을 만들었습니다."); } catch (error) { setMessage(error instanceof Error ? error.message : "이력서 초안 생성에 실패했습니다."); } finally { setLoading(false); } };
  return <div className="resume-document-section">
    <div className="resume-document-intro"><div><span className="eyebrow">RESUME INTELLIGENCE</span><h2>이력서에서 스펙을 읽고, 다시 이력서로 완성하세요.</h2><p>업로드한 PDF/DOCX에서 핵심 정보를 추출해 역량 프로필에 반영할 수 있습니다. 원본 파일은 저장하지 않고, 회원별 추출 결과와 초안만 안전하게 저장합니다.</p></div><FileText size={34} /></div>
    <div className="resume-document-grid">
      <article className="resume-document-card"><h3><Upload size={18} /> 이력서 업로드 · 스펙 추출</h3><p>PDF 또는 DOCX, 최대 5MB. 자동 반영 전에는 항상 제안 내용을 먼저 보여줍니다.</p><input ref={inputRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><div className="form-actions"><button className="primary-button" disabled={loading || !file} onClick={upload}>{loading ? "분석 중..." : "이력서 분석"}</button></div></article>
      <article className="resume-document-card"><h3><Sparkles size={18} /> 내 스펙으로 이력서 초안</h3><p>역량 프로필·자격증·프로젝트·자기소개 내용을 바탕으로 수정 가능한 DOCX 초안을 생성합니다.</p><textarea value={request} onChange={(event) => setRequest(event.target.value)} placeholder="예: 백엔드 주니어 지원용으로 프로젝트 성과를 강조해 주세요." rows={4} /><div className="form-actions"><button className="outline-button" disabled={loading} onClick={generate}>{loading ? "작성 중..." : "초안 만들기"}</button></div></article>
    </div>
    {message && <p className="resume-document-message">{message}</p>}
    <div className="resume-document-list"><h3>내 이력서 자료</h3>{documents.length === 0 ? <p className="empty-state">아직 저장된 이력서 자료가 없습니다.</p> : documents.map((document) => <article key={document.id} className="resume-document-item"><div><span>{document.type === "UPLOADED" ? "업로드 분석" : "생성 초안"}</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}</small>{document.type === "UPLOADED" && document.extractedProfile && <p>제안 직무: {String(document.extractedProfile.targetRole || "미확인")} · 기술: {(document.extractedProfile.suggestedSkills as string[] | undefined)?.join(", ") || "미확인"}</p>}</div><div className="form-actions">{document.type === "UPLOADED" && <button className="outline-button" disabled={loading} onClick={() => apply(document.id)}>프로필 반영</button>}{document.type === "GENERATED" && <a className="primary-button" href={`/api/v1/members/me/resume-documents/${document.id}/download.docx`}><Download size={15} /> DOCX 내려받기</a>}</div></article>)}</div>
  </div>;
}
