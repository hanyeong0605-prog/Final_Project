import React, { useRef, useState } from "react";
import Webcam from "react-webcam";
import axios from "axios";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  adminId?: string;
}

export const AdminFaceAuthModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onSuccess,
  adminId = "local-dev",
}) => {
  const webcamRef = useRef<Webcam>(null);
  const [loading, setLoading] = useState(false);

  if (!isOpen) return null;

  const handleVerify = async () => {
    if (!webcamRef.current) return;

    const imageSrc = webcamRef.current.getScreenshot();
    if (!imageSrc) {
      alert("카메라 화면을 불러올 수 없습니다.");
      return;
    }

    setLoading(true);
    try {
      const aiBaseUrl = import.meta.env.VITE_AI_API_BASE_URL || "http://localhost:8000";
      const response = await axios.post(`${aiBaseUrl}/api/admin/face/verify`, {
        admin_id: String(adminId || "local-dev"),
        image_base64: imageSrc,
      });

      if (response.data.verified) {
        onSuccess?.();
        alert(`인증 성공 (일치율: ${response.data.similarity}%)`);
      } else {
        alert(`인증 실패: ${response.data.message}`);
      }
    } catch (error: any) {
      console.error("화상 인증 실패:", error);
      const detail = error.response?.data?.detail;
      const message =
        typeof detail === "object" ? JSON.stringify(detail) : detail || error.message || "오류가 발생했습니다.";
      alert(`오류: ${message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.6)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999
    }}>
      <div style={{ background: "#fff", padding: "24px", borderRadius: "12px", textAlign: "center", width: "440px" }}>
        <h3 style={{ marginBottom: "16px", fontSize: "1.25rem", fontWeight: "bold" }}>관리자 화상 보안 인증</h3>
        
        <div style={{ borderRadius: "8px", overflow: "hidden", backgroundColor: "#000", marginBottom: "16px" }}>
          <Webcam audio={false} ref={webcamRef} screenshotFormat="image/jpeg" width={400} height={300} />
        </div>

        <div style={{ display: "flex", gap: "8px", justifyContent: "center" }}>
          <button
            onClick={handleVerify}
            disabled={loading}
            style={{
              padding: "10px 20px", backgroundColor: "#2563eb", color: "#fff",
              border: "none", borderRadius: "6px", cursor: loading ? "not-allowed" : "pointer"
            }}
          >
            {loading ? "얼굴 분석 중..." : "인증하기"}
          </button>
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