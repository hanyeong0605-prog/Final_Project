import { useEffect, useState } from "react";
import { Camera, CheckCircle2, Upload, X } from "lucide-react";
import { getAdminFaceReferences, uploadAdminFaceReference, type AdminFaceReference } from "../api/adminFaceReferenceApi";

type Props = { loginId: string; onClose: () => void; onSaved?: () => void };

/** Face reference enrollment happens in-context, only after the operator passed face verification. */
export function AdminFaceReferenceModal({ loginId, onClose, onSaved }: Props) {
  const [admins, setAdmins] = useState<AdminFaceReference[]>([]);
  const [photo, setPhoto] = useState<File | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const selected = admins.find((admin) => admin.loginId === loginId);

  useEffect(() => {
    getAdminFaceReferences().then(setAdmins)
      .catch((reason) => setError(reason instanceof Error ? reason.message : "관리자 목록을 불러오지 못했습니다."));
  }, []);

  const save = async () => {
    if (!photo || saving) return;
    setSaving(true); setError(""); setMessage("");
    try {
      const result = await uploadAdminFaceReference(loginId, photo);
      setAdmins((items) => items.map((item) => item.loginId === result.loginId ? result : item));
      setMessage(`${result.nickname} 관리자 기준 사진을 등록했습니다.`);
      setPhoto(null);
      onSaved?.();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "기준 사진 등록에 실패했습니다."); }
    finally { setSaving(false); }
  };

  return <div role="dialog" aria-modal="true" aria-label="관리자 얼굴 사진 등록" style={{ position: "fixed", inset: 0, zIndex: 10000, background: "rgba(15, 23, 42, .55)", display: "grid", placeItems: "center", padding: 20 }}>
    <section className="auth-card" style={{ width: "min(560px, 100%)", maxWidth: 560, position: "relative", margin: 0 }}>
      <button className="icon-button" aria-label="사진 등록 창 닫기" onClick={onClose} style={{ position: "absolute", right: 16, top: 16 }}><X size={18} /></button>
      <div className="brand-mark"><Camera size={22} /></div>
      <h1>관리자 기준 사진 등록</h1>
      <p><b>{selected?.nickname ?? loginId}</b> 계정의 정면 얼굴 사진을 등록하거나 교체합니다. 사진은 EC2 비공개 저장소에만 보관됩니다.</p>
      {error && <p style={{ color: "#c0392b" }}>{error}</p>}
      {message && <p style={{ color: "#16803c" }}><CheckCircle2 size={16} /> {message}</p>}
      <label style={{ display: "grid", gap: 8, textAlign: "left", marginTop: 18 }}>정면 얼굴 사진
        <input type="file" accept="image/jpeg,image/png" capture="user" onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} />
      </label>
      <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
        <button className="outline-button" type="button" onClick={onClose} style={{ flex: 1 }}>닫기</button>
        <button className="primary-button" type="button" disabled={!photo || saving || !selected} onClick={() => void save()} style={{ flex: 1 }}><Upload size={17} /> {saving ? "사진 저장 중" : "사진 등록"}</button>
      </div>
    </section>
  </div>;
}
