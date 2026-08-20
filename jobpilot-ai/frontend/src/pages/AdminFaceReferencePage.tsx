import { useEffect, useState } from "react";
import { Camera, CheckCircle2, Upload } from "lucide-react";
import { getAdminFaceReferences, uploadAdminFaceReference, type AdminFaceReference } from "../features/admin/api/adminFaceReferenceApi";

export function AdminFaceReferencePage() {
  const [admins, setAdmins] = useState<AdminFaceReference[]>([]);
  const [selectedLoginId, setSelectedLoginId] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getAdminFaceReferences().then((items) => {
      setAdmins(items);
      setSelectedLoginId(items[0]?.loginId ?? "");
    }).catch((reason) => setError(reason instanceof Error ? reason.message : "관리자 목록을 불러오지 못했습니다."));
  }, []);

  const save = async () => {
    if (!selectedLoginId || !photo || saving) return;
    setSaving(true); setError(""); setMessage("");
    try {
      const result = await uploadAdminFaceReference(selectedLoginId, photo);
      setAdmins((items) => items.map((item) => item.loginId === result.loginId ? result : item));
      setMessage(`${result.nickname} (${result.loginId}) 관리자 기준 사진을 등록했습니다.`);
      setPhoto(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "기준 사진 등록에 실패했습니다."); }
    finally { setSaving(false); }
  };

  return <main className="auth-page"><section className="auth-card" style={{ maxWidth: 560 }}>
    <div className="brand-mark"><Camera size={22} /></div>
    <h1>관리자 기준 사진 등록</h1>
    <p>관리자 계정을 선택해 정면 얼굴 사진을 등록하거나 교체합니다. 사진은 EC2의 비공개 저장소에만 보관됩니다.</p>
    {error && <p style={{ color: "#c0392b" }}>{error}</p>}
    {message && <p style={{ color: "#16803c" }}><CheckCircle2 size={16} /> {message}</p>}
    <label style={{ display: "grid", gap: 8, textAlign: "left", marginTop: 18 }}>관리자 계정
      <select value={selectedLoginId} onChange={(event) => setSelectedLoginId(event.target.value)}>
        {admins.map((admin) => <option key={admin.loginId} value={admin.loginId}>{admin.nickname} · {admin.loginId} {admin.registered ? "(등록됨)" : "(미등록)"}</option>)}
      </select>
    </label>
    <label style={{ display: "grid", gap: 8, textAlign: "left", marginTop: 16 }}>정면 얼굴 사진
      <input type="file" accept="image/jpeg,image/png" capture="user" onChange={(event) => setPhoto(event.target.files?.[0] ?? null)} />
    </label>
    <button className="primary-button" type="button" disabled={!selectedLoginId || !photo || saving} onClick={() => void save()} style={{ width: "100%", marginTop: 20 }}>
      <Upload size={17} /> {saving ? "사진 저장 중" : "기준 사진 등록"}
    </button>
  </section></main>;
}
