import { FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Pencil, Trash2 } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import {
  createJobPosting, getMyJobPostings, hideJobPosting, updateJobPosting,
  type EmployerJobPosting, type EmployerJobPostingInput,
} from "../features/employer/api/employerJobPostingApi";

const emptyForm: EmployerJobPostingInput = {
  title: "", companyUrl: "", description: "", location: "", employmentType: "", experienceType: "",
  salary: "", deadlineAt: "", rollingDeadline: true,
};

export function EmployerMyPage() {
  const { employer, logout } = useEmployerAuth();
  const navigate = useNavigate();
  const [postings, setPostings] = useState<EmployerJobPosting[]>([]);
  const [form, setForm] = useState<EmployerJobPostingInput>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const approved = employer?.status === "APPROVED";

  const load = async () => {
    if (!approved) return;
    try {
      const result = await getMyJobPostings();
      setPostings(result.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : "채용공고 목록을 불러오지 못했습니다.");
    }
  };

  useEffect(() => { void load(); }, [approved]);

  if (!employer) return null;

  const update = (field: keyof EmployerJobPostingInput) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    const value = field === "rollingDeadline" ? (event.target as HTMLInputElement).checked : event.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const startEdit = (posting: EmployerJobPosting) => {
    setEditingId(posting.id);
    setForm({
      title: posting.title, companyUrl: posting.companyUrl ?? "", description: posting.description ?? "",
      location: posting.location ?? "", employmentType: posting.employmentType ?? "", experienceType: posting.experienceType ?? "",
      salary: posting.salary ?? "", deadlineAt: posting.deadlineAt ? posting.deadlineAt.slice(0, 16) : "",
      rollingDeadline: posting.rollingDeadline,
    });
  };

  const cancelEdit = () => { setEditingId(null); setForm(emptyForm); };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(""); setNotice("");
    setSubmitting(true);
    try {
      const payload: EmployerJobPostingInput = { ...form, deadlineAt: form.deadlineAt || null };
      if (editingId) {
        await updateJobPosting(editingId, payload);
        setNotice("채용공고를 수정했습니다.");
      } else {
        await createJobPosting(payload);
        setNotice("채용공고를 등록했습니다.");
      }
      cancelEdit();
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const remove = async (posting: EmployerJobPosting) => {
    if (!window.confirm(`'${posting.title}' 공고를 숨김 처리할까요?`)) return;
    setError(""); setNotice("");
    try {
      await hideJobPosting(posting.id);
      setNotice("공고를 숨김 처리했습니다.");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "처리에 실패했습니다.");
    }
  };

  return (
    <main className="content">
      <PageHeading
        eyebrow="EMPLOYER"
        title={employer.companyName}
        body={`${employer.managerName} 담당자님, 환영합니다.`}
        action={<button className="outline-button" onClick={() => { logout(); navigate("/employer/login"); }}>로그아웃</button>}
      />

      {employer.status === "PENDING" && (
        <div className="account-alert">
          가입 심사 중입니다. {employer.ntsVerified ? "사업자 진위확인은 완료됐고, " : "사업자 정보 확인이 필요해서, "}
          관리자 승인이 끝나면 채용공고를 등록할 수 있어요.
        </div>
      )}
      {employer.status === "REJECTED" && (
        <div className="account-alert error">
          가입이 거절되었습니다{employer.rejectionReason ? `: ${employer.rejectionReason}` : "."} 문의가 필요하면 관리자에게 연락해 주세요.
        </div>
      )}
      {(notice || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || notice}</div>}

      {approved && (
        <>
          <section className="panel admin-panel">
            <div className="admin-panel-heading">
              <div><span className="eyebrow">JOB POSTING</span><h2>{editingId ? "채용공고 수정" : "채용공고 등록"}</h2></div>
            </div>
            <form onSubmit={submit} className="employer-posting-form">
              <label>제목<input required value={form.title} onChange={update("title")} /></label>
              <label>상세 설명<textarea required rows={5} value={form.description} onChange={update("description")} /></label>
              <label>근무지<input value={form.location} onChange={update("location")} /></label>
              <label>고용 형태<input value={form.employmentType} onChange={update("employmentType")} placeholder="정규직/계약직 등" /></label>
              <label>경력 조건<input value={form.experienceType} onChange={update("experienceType")} placeholder="신입/경력 등" /></label>
              <label>급여<input value={form.salary} onChange={update("salary")} /></label>
              <label>회사 홈페이지<input value={form.companyUrl} onChange={update("companyUrl")} /></label>
              <label>마감일시<input type="datetime-local" value={form.deadlineAt ?? ""} onChange={update("deadlineAt")} disabled={form.rollingDeadline} /></label>
              <label className="employer-posting-checkbox">
                <input type="checkbox" checked={form.rollingDeadline} onChange={update("rollingDeadline")} /> 상시 채용(마감일 없음)
              </label>
              <div className="admin-table-actions">
                <button className="primary-button" disabled={submitting}>{submitting ? "저장 중..." : editingId ? "수정 저장" : "등록"}</button>
                {editingId && <button type="button" className="outline-button" onClick={cancelEdit}>취소</button>}
              </div>
            </form>
          </section>

          <section className="panel admin-panel">
            <div className="admin-panel-heading"><div><span className="eyebrow">MY POSTINGS</span><h2>내 채용공고</h2></div></div>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead><tr><th>공고</th><th>근무지</th><th>마감일</th><th>조회</th><th>상태</th><th>관리</th></tr></thead>
                <tbody>
                  {postings.map((posting) => (
                    <tr key={posting.id}>
                      <td><strong>{posting.title}</strong></td>
                      <td>{posting.location || "-"}</td>
                      <td>{posting.rollingDeadline ? "상시" : posting.deadlineAt ? new Date(posting.deadlineAt).toLocaleDateString("ko-KR") : "-"}</td>
                      <td>{posting.viewCount.toLocaleString()}</td>
                      <td><span className={`admin-role-badge status-${posting.status.toLowerCase()}`}>{posting.status}</span></td>
                      <td>
                        <div className="admin-table-actions">
                          <button className="icon-button" title="수정" onClick={() => startEdit(posting)}><Pencil size={15} /></button>
                          <button className="icon-button danger" title="숨김" onClick={() => void remove(posting)}><Trash2 size={15} /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {postings.length === 0 && <tr><td colSpan={6}>등록된 채용공고가 없습니다.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </main>
  );
}
