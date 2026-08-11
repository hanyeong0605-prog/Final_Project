import { useEffect, useState } from "react";
import {
  createSelfIntroduction,
  deleteSelfIntroduction,
  listSelfIntroductions,
  updateSelfIntroduction,
} from "../api/resumeApi";
import {
  critiqueSelfIntroduction,
  fetchSelfIntroductionQuestions,
  generateSelfIntroductionDraft,
} from "../api/resumeAiApi";
import type { SelfIntroduction, SelfIntroductionCritiqueResult } from "../model/resume.types";

// 2026-08-10: 자기소개서 작성/관리 섹션 - 태스크 #61. "질문식으로 작성" vs "직접 쓰고
// 첨삭받기" 둘 다 지원한다(사용자 요청). 생성(ai-server)과 저장(백엔드 CRUD)이 분리돼
// 있어서 - 여기서 그 둘을 순서대로 조합한다: 생성 결과를 그대로 저장하지 않고, 사용자가
// 미리보기에서 한 번 더 수정할 수 있게 편집 가능한 textarea에 채워준다(AI가 지어낸 내용을
// 그대로 믿지 않고 검토하게 하려는 의도).
interface Props {
  job: string;
  techSummary: string;
}

type EditorMode = "guided" | "freeform" | null;

export function SelfIntroductionSection({ job, techSummary }: Props) {
  const [entries, setEntries] = useState<SelfIntroduction[]>([]);
  const [loading, setLoading] = useState(true);
  const [questions, setQuestions] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [editorMode, setEditorMode] = useState<EditorMode>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [title, setTitle] = useState("");
  const [primary, setPrimary] = useState(false);
  const [answers, setAnswers] = useState<string[]>([]);
  const [content, setContent] = useState("");
  const [critique, setCritique] = useState<SelfIntroductionCritiqueResult | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void Promise.all([listSelfIntroductions(), fetchSelfIntroductionQuestions()])
      .then(([list, q]) => { setEntries(list); setQuestions(q.questions); setAnswers(q.questions.map(() => "")); })
      .catch(() => setErrorMessage("자기소개서 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  const resetEditor = () => {
    setEditorMode(null); setEditingId(null); setTitle(""); setPrimary(false);
    setAnswers(questions.map(() => "")); setContent(""); setCritique(null); setErrorMessage(null);
  };

  const startGuided = () => { resetEditor(); setEditorMode("guided"); };
  const startFreeform = () => { resetEditor(); setEditorMode("freeform"); };
  const startEdit = (entry: SelfIntroduction) => {
    resetEditor();
    setEditorMode("freeform"); setEditingId(entry.id); setTitle(entry.title);
    setContent(entry.content); setPrimary(entry.primary);
  };

  const handleGenerate = async () => {
    setBusy(true); setErrorMessage(null);
    try {
      const result = await generateSelfIntroductionDraft(job, techSummary, answers);
      if (!result.ok || !result.content) { setErrorMessage(result.message ?? "생성에 실패했습니다."); return; }
      setContent(result.content);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "생성 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleCritique = async () => {
    setBusy(true); setErrorMessage(null); setCritique(null);
    try {
      const result = await critiqueSelfIntroduction(content, job, techSummary);
      if (!result.ok) { setErrorMessage(result.message ?? "첨삭에 실패했습니다."); return; }
      setCritique(result);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "첨삭 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!title.trim() || !content.trim()) { setErrorMessage("제목과 내용을 모두 입력해주세요."); return; }
    setBusy(true); setErrorMessage(null);
    try {
      const input = { title: title.trim(), content: content.trim(), primary };
      const saved = editingId ? await updateSelfIntroduction(editingId, input) : await createSelfIntroduction(input);
      setEntries((prev) => {
        const withoutSaved = prev.filter((e) => e.id !== saved.id);
        // 대표로 지정했으면 다른 항목의 primary는 백엔드가 이미 꺼줬으니, 프론트 목록도 같이 갱신.
        const cleared = saved.primary ? withoutSaved.map((e) => ({ ...e, primary: false })) : withoutSaved;
        return [saved, ...cleared];
      });
      resetEditor();
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "저장 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm("이 자기소개서를 삭제할까요?")) return;
    try {
      await deleteSelfIntroduction(id);
      setEntries((prev) => prev.filter((e) => e.id !== id));
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "삭제 중 오류가 발생했습니다.");
    }
  };

  if (loading) return <p>불러오는 중...</p>;

  return (
    <div>
      {errorMessage && <div className="auth-error">{errorMessage}</div>}

      {!editorMode && (
        <>
          <div className="form-actions" style={{ marginBottom: 16 }}>
            <button className="primary-button" onClick={startGuided}>질문식으로 작성</button>
            <button className="outline-button" onClick={startFreeform}>직접 쓰고 첨삭받기</button>
          </div>

          {entries.length === 0 && <p>아직 작성한 자기소개서가 없습니다.</p>}
          {entries.map((entry) => (
            <div key={entry.id} className="panel" style={{ marginBottom: 12, padding: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <strong>{entry.title}{entry.primary && <span style={{ marginLeft: 8, fontSize: 12, color: "#596ff3" }}>대표</span>}</strong>
                <div className="form-actions">
                  <button className="outline-button" onClick={() => startEdit(entry)}>수정</button>
                  <button className="outline-button" onClick={() => handleDelete(entry.id)}>삭제</button>
                </div>
              </div>
              <p style={{ whiteSpace: "pre-wrap", marginTop: 8 }}>{entry.content}</p>
            </div>
          ))}
        </>
      )}

      {editorMode === "guided" && (
        <div>
          <h3>질문에 답해주세요</h3>
          {questions.map((q, i) => (
            <div className="form-section" key={q}>
              <div className="form-fields">
                <label className="wide">
                  {q}
                  <textarea
                    rows={3}
                    value={answers[i] ?? ""}
                    onChange={(e) => setAnswers((prev) => prev.map((a, idx) => (idx === i ? e.target.value : a)))}
                    placeholder="답변을 입력해주세요 (건너뛰어도 됩니다)"
                  />
                </label>
              </div>
            </div>
          ))}
          <div className="form-actions">
            <button className="primary-button" disabled={busy} onClick={handleGenerate}>
              {busy ? "생성 중..." : "자기소개서 생성하기"}
            </button>
          </div>

          {content && (
            <div className="form-section">
              <h3>생성된 자기소개서 (자유롭게 수정하세요)</h3>
              <div className="form-fields">
                <label className="wide">
                  <textarea rows={10} value={content} onChange={(e) => setContent(e.target.value)} />
                </label>
              </div>
            </div>
          )}
          {renderSaveFields()}
        </div>
      )}

      {editorMode === "freeform" && (
        <div>
          <h3>{editingId ? "자기소개서 수정" : "자기소개서 직접 작성"}</h3>
          <div className="form-section">
            <div className="form-fields">
              <label className="wide">
                내용
                <textarea rows={10} value={content} onChange={(e) => setContent(e.target.value)} placeholder="자기소개서 내용을 붙여넣거나 직접 작성해주세요." />
              </label>
            </div>
          </div>
          <div className="form-actions">
            <button className="outline-button" disabled={busy || !content.trim()} onClick={handleCritique}>
              {busy ? "분석 중..." : "AI 첨삭받기"}
            </button>
          </div>

          {critique && (
            <div className="panel" style={{ padding: 16, marginTop: 12 }}>
              {critique.strengths.length > 0 && (
                <div><strong>잘한 점</strong><ul>{critique.strengths.map((s) => <li key={s}>{s}</li>)}</ul></div>
              )}
              {critique.improvements.length > 0 && (
                <div><strong>개선하면 좋을 점</strong><ul>{critique.improvements.map((s) => <li key={s}>{s}</li>)}</ul></div>
              )}
              {critique.revised_example && (
                <div><strong>수정 예시</strong><p style={{ whiteSpace: "pre-wrap" }}>{critique.revised_example}</p></div>
              )}
            </div>
          )}
          {renderSaveFields()}
        </div>
      )}
    </div>
  );

  function renderSaveFields() {
    if (!content.trim()) return null;
    return (
      <div className="form-section">
        <div className="form-fields">
          <label>제목<input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 백엔드 개발자 지원용" /></label>
          <label style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <input type="checkbox" checked={primary} onChange={(e) => setPrimary(e.target.checked)} />
            대표 자기소개서로 지정
          </label>
        </div>
        <div className="form-actions">
          <button className="outline-button" onClick={resetEditor}>취소</button>
          <button className="primary-button" disabled={busy} onClick={handleSave}>{busy ? "저장 중..." : "저장"}</button>
        </div>
      </div>
    );
  }
}
