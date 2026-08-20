import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { LoaderCircle, Smartphone } from "lucide-react";
import { createAdminFacePairing, getAdminFacePairingResult, type AdminFacePairing } from "../api/adminFacePairingApi";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export const AdminFaceAuthModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [pairing, setPairing] = useState<AdminFacePairing | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    let disposed = false;
    createAdminFacePairing().then((created) => { if (!disposed) setPairing(created); })
      .catch((reason) => { if (!disposed) setError(reason instanceof Error ? reason.message : "QR을 생성하지 못했습니다."); });
    return () => { disposed = true; };
  }, [isOpen]);

  useEffect(() => {
    if (!pairing || !isOpen) return;
    const timer = window.setInterval(() => {
      getAdminFacePairingResult(pairing.sessionId).then((result) => {
        if (result.status === "VERIFIED") { window.clearInterval(timer); onSuccess?.(); }
        if (result.status === "REJECTED") setError(result.message ?? "얼굴 인증에 실패했습니다. 휴대폰에서 다시 시도해 주세요.");
      }).catch((reason) => { window.clearInterval(timer); setError(reason instanceof Error ? reason.message : "인증 상태를 확인하지 못했습니다."); });
    }, 1500);
    return () => window.clearInterval(timer);
  }, [pairing, isOpen, onSuccess]);

  if (!isOpen) return null;

  const pairUrl = pairing ? `${window.location.origin}/admin-face-pair?session=${encodeURIComponent(pairing.sessionId)}&token=${encodeURIComponent(pairing.token)}` : "";

  return (
    <div style={{
      position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.6)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999
    }}>
      <div style={{ background: "#fff", padding: "24px", borderRadius: "12px", textAlign: "center", width: "440px" }}>
        <h3 style={{ marginBottom: "8px", fontSize: "1.25rem", fontWeight: "bold" }}>관리자 휴대폰 얼굴 인증</h3>
        <p style={{ color: "#667085", fontSize: 14 }}>휴대폰으로 QR을 스캔한 뒤 같은 관리자 계정으로 촬영해 주세요.</p>
        {error && <p style={{ color: "#c0392b", fontSize: 13 }}>{error}</p>}
        {!pairing && !error && <LoaderCircle className="spin" size={28} style={{ margin: "28px auto" }} />}
        {pairing && <div style={{ display: "grid", justifyItems: "center", gap: 10, margin: "20px 0" }}><QRCodeSVG value={pairUrl} size={220} level="M" includeMargin /><small><Smartphone size={13} /> QR은 2분 동안 한 번만 사용할 수 있습니다.</small></div>}

        <div style={{ display: "flex", gap: "8px", justifyContent: "center" }}>
          <Link to="/admin/face-references" onClick={onClose} className="outline-button">기준 사진 등록</Link>
          <button
            onClick={onClose}
            style={{
              padding: "10px 20px", backgroundColor: "#9ca3af", color: "#fff",
              border: "none", borderRadius: "6px", cursor: "pointer"
            }}
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
};
