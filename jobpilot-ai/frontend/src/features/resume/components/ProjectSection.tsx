import { useEffect, useState } from "react";
import { createProject, deleteProject, listProjects, updateProject } from "../api/resumeApi";
import { critiqueProject, fetchProjectQuestions, generateProjectDraft } from "../api/resumeAiApi";
import type { Project, ProjectCritiqueResult } from "../model/resume.types";

// 2026-08-10: 프로젝트 경험(STAR) 작성/관리 섹션 - 태스크 #62. SelfIntroductionSection과
// 같은 원칙이지만, Project는 role/problem/solution/result 4개 필드로 나뉘어 있어서 생성
// 결과도 문단 하나가 아니라 4개 필드 각각으로 온다(project.py generate_draft 참고).
interface Props {
  job: string;
  techSummary: string;
}

type EditorMode = "guided" | "freeform" | null;

const emptyFields = { roleDescription: "", problemDescription: "", solutionDescription: "", resultDescription: "" };

export function ProjectSection({ job, techSummary }: Props) {
  const [entries, setEntries] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [questions, setQuestions] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [editorMode, setEditorMode] = useState<EditorMode>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [title, setTitle] = useState("");
  const [answers, setAnswers] = useState<string[]>([]);
  const [fields, setFields] = useState(emptyFields);
  const [githubUrl, setGithubUrl] = useState("");
  const [deploymentUrl, setDeploymentUrl] = useState("");
  const [critique, setCritique] = useState<ProjectCritiqueResult | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void Promise.all([listProjects(), fetchProjectQuestions()])
      .then(([list, q]) => { setEntries(list); setQuestions(q.questions); setAnswers(q.questions.map(() => "")); })
      .catch(() => setErrorMessage("프로젝트 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  const resetEditor = () => {
    setEditorMode(null); setEditingId(null); setTitle(""); setAnswers(questions.map(() => ""));
    setFields(emptyFields); setGithubUrl(""); setDeploymentUrl(""); setCritique(null); setErrorMessage(null);
  };

  const startGuided = () => { resetEditor(); setEditorMode("guided"); };
  const startFreeform = () => { resetEditor(); setEditorMode("freeform"); };
  const startEdit = (entry: Project) => {
    resetEditor();
    setEditorMode("freeform"); setEditingId(entry.id); setTitle(entry.title);
    setFields({
      roleDescription: entry.roleDescription ?? "", problemDescription: entry.problemDescription ?? "",
      solutionDescription: entry.solutionDescription ?? "", resultDescription: entry.resultDescription ?? "",
    });
    setGithubUrl(entry.githubUrl ?? ""); setDeploymentUrl(entry.deploymentUrl ?? "");
  };

  const handleGenerate = async () => {
    setBusy(true); setErrorMessage(null);
    try {
      const result = await generateProjectDraft(title, job, techSummary, answers);
      if (!result.ok) { setErrorMessage(result.message ?? "생성에 실패했습니다."); return; }
      setFields({
        roleDescription: result.role_description ?? "", problemDescription: result.problem_description ?? "",
        solutionDescription: result.solution_description ?? "", resultDescription: result.result_description ?? "",
      });
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "생성 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleCritique = async () => {
    setBusy(true); setErrorMessage(null); setCritique(null);
    try {
      const result = await critiqueProject(fields, job, techSummary);
      if (!result.ok) { setErrorMessage(result.message ?? "첨삭에 실패했습니다."); return; }
      setCritique(result);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "첨삭 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!title.trim()) { setErrorMessage("프로젝트명을 입력해주세요."); return; }
    setBusy(true); setErrorMessage(null);
    try {
      const input = {
        title: title.trim(),
        roleDescription: fields.roleDescription.trim() || null,
        problemDescription: fields.problemDescription.trim() || null,
        solutionDescription: fields.solutionDescription.trim() || null,
        resultDescription: fields.resultDescription.trim() || null,
        githubUrl: githubUrl.trim() || null,
        deploymentUrl: deploymentUrl.trim() || null,
        startedAt: null,
        endedAt: null,
      };
      const saved = editingId ? await updateProject(editingId, input) : await createProject(input);
      setEntries((prev) => [saved, ...prev.filter((e) => e.id !== saved.id)]);
      resetEditor();
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "저장 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm("이 프로젝트를 삭제할까요?")) return;
    try {
      await deleteProject(id);
      setEntries((prev) => prev.filter((e) => e.id !== id));
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "삭제 중 오류가 발생했습니다.");
    }
  };

  const hasAnyField = Object.values(fields).some((v) => v.trim());

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

          {entries.length === 0 && <p>아직 등록한 프로젝트 경험이 없습니다.</p>}
          {entries.map((entry) => (
            <div key={entry.id} className="panel" style={{ marginBottom: 12, padding: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <strong>{entry.title}</strong>
                <div className="form-actions">
                  <button className="outline-button" onClick={() => startEdit(entry)}>수정</button>
                  <button className="outline-button" onClick={() => handleDelete(entry.id)}>삭제</button>
                </div>
              </div>
              {entry.roleDescription && <p><strong>역할</strong> {entry.roleDescription}</p>}
              {entry.problemDescription && <p><strong>문제</strong> {entry.problemDescription}</p>}
              {entry.solutionDescription && <p><strong>해결</strong> {entry.solutionDescription}</p>}
              {entry.resultDescription && <p><strong>결과</strong> {entry.resultDescription}</p>}
            </div>
          ))}
        </>
      )}

      {(editorMode === "guided" || editorMode === "freeform") && (
        <div>
          <div className="form-section">
            <div className="form-fields">
              <label className="wide">프로젝트명<input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 커머스 플랫폼 백엔드 개발" /></label>
            </div>
          </div>

          {editorMode === "guided" && (
            <>
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
                  {busy ? "생성 중..." : "프로젝트 설명 생성하기"}
                </button>
              </div>
            </>
          )}

          {(editorMode === "freeform" || hasAnyField) && (
            <div className="form-section">
              <h3>{editorMode === "guided" ? "생성된 내용 (자유롭게 수정하세요)" : "프로젝트 설명"}</h3>
              <div className="form-fields">
                <label className="wide">역할<textarea rows={2} value={fields.roleDescription} onChange={(e) => setFields((f) => ({ ...f, roleDescription: e.target.value }))} /></label>
                <label className="wide">문제/과제<textarea rows={2} value={fields.problemDescription} onChange={(e) => setFields((f) => ({ ...f, problemDescription: e.target.value }))} /></label>
                <label className="wide">해결<textarea rows={2} value={fields.solutionDescription} onChange={(e) => setFields((f) => ({ ...f, solutionDescription: e.target.value }))} /></label>
                <label className="wide">결과<textarea rows={2} value={fields.resultDescription} onChange={(e) => setFields((f) => ({ ...f, resultDescription: e.target.value }))} /></label>
                <label className="wide">GitHub 링크(선택)<input value={githubUrl} onChange={(e) => setGithubUrl(e.target.value)} /></label>
                <label className="wide">배포 링크(선택)<input value={deploymentUrl} onChange={(e) => setDeploymentUrl(e.target.value)} /></label>
              </div>
            </div>
          )}

          {editorMode === "freeform" && (
            <div className="form-actions">
              <button className="outline-button" disabled={busy || !hasAnyField} onClick={handleCritique}>
                {busy ? "분석 중..." : "AI 첨삭받기"}
              </button>
            </div>
          )}

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

          <div className="form-actions">
            <button className="outline-button" onClick={resetEditor}>취소</button>
            <button className="primary-button" disabled={busy} onClick={handleSave}>{busy ? "저장 중..." : "저장"}</button>
          </div>
        </div>
      )}
    </div>
  );
}
