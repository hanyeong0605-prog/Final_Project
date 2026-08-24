import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Camera, CheckCircle2, LoaderCircle, LogIn, Smartphone } from "lucide-react";
import { useAuth } from "../features/auth/model/AuthContext";
import { submitAdminFaceCapture } from "../features/admin/api/adminFacePairingApi";

export function AdminFacePairPage() {
  const { member, loading } = useAuth();
  const [params] = useSearchParams();
  const sessionId = params.get("session") ?? "";
  const token = params.get("token") ?? "";
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [ready, setReady] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("전면 카메라를 준비하고 있습니다.");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!member || !sessionId || !token) return;
    let disposed = false;
    navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 960 }, height: { ideal: 540 } }, audio: false })
      .then((stream) => {
        if (disposed) { stream.getTracks().forEach((track) => track.stop()); return; }
        streamRef.current = stream;
        if (videoRef.current) videoRef.current.srcObject = stream;
        setReady(true);
        setMessage("얼굴을 화면 중앙에 맞춘 뒤 인증을 눌러 주세요.");
      })
      .catch(() => setError("카메라 권한을 허용해 주세요. 휴대폰 브라우저는 HTTPS 연결이 필요합니다."));
    return () => { disposed = true; streamRef.current?.getTracks().forEach((track) => track.stop()); };
  }, [member, sessionId, token]);

  const capture = async () => {
    const video = videoRef.current;
    if (!video || !ready || submitting) return;
    if (!video.videoWidth || !video.videoHeight) {
      setError("카메라 영상을 준비 중입니다. 잠시 후 다시 눌러 주세요.");
      return;
    }
    const canvas = document.createElement("canvas");
    // RetinaFace does not need a phone's full-resolution frame for a single
    // centered face. Limiting the upload cuts mobile transfer and server-side
    // detection work without weakening the matching threshold.
    const scale = Math.min(1, 720 / video.videoWidth);
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    canvas.getContext("2d")?.drawImage(video, 0, 0, canvas.width, canvas.height);
    const imageBase64 = canvas.toDataURL("image/jpeg", 0.82);
    setSubmitting(true); setError(null); setMessage("얼굴을 안전하게 확인하고 있습니다.");
    try {
      const result = await submitAdminFaceCapture(sessionId, token, imageBase64);
      if (result.status === "VERIFIED") {
        setMessage(`인증되었습니다. PC 화면으로 돌아가세요. (일치율 ${result.similarity ?? "-"}%)`);
        streamRef.current?.getTracks().forEach((track) => track.stop());
      } else {
        setError(`${result.message ?? "등록 사진과 일치하지 않습니다."} QR을 새로 만들 필요 없이 이 화면에서 다시 인증할 수 있습니다.`);
        setMessage("얼굴 위치나 조명을 조정한 뒤 다시 인증해 주세요.");
      }
    } catch (reason) { setError(reason instanceof Error ? reason.message : "인증 처리에 실패했습니다."); }
    finally { setSubmitting(false); }
  };

  const returnTo = `/admin-face-pair?${params.toString()}`;
  if (loading) return <div className="auth-loading">로그인 상태를 확인하고 있습니다.</div>;
  if (!sessionId || !token) return <main className="auth-page"><section className="auth-card"><h1>잘못된 QR 코드입니다.</h1><p>PC에서 새 QR 코드를 생성해 주세요.</p></section></main>;
  if (!member) return <main className="auth-page"><section className="auth-card"><Smartphone size={32} /><h1>관리자 계정으로 로그인해 주세요</h1><p>PC에서 로그인한 동일한 관리자 계정이어야 합니다.</p><Link className="primary-button" to={`/login?returnTo=${encodeURIComponent(returnTo)}`}><LogIn size={16} /> 로그인</Link></section></main>;

  return <main className="auth-page"><section className="auth-card" style={{ maxWidth: 520 }}>
    <div className="brand-mark"><Camera size={22} /></div><h1>관리자 휴대폰 얼굴 인증</h1><p>{message}</p>
    {error && <p style={{ color: "#c0392b" }}>{error}</p>}
    <video ref={videoRef} autoPlay muted playsInline style={{ width: "100%", borderRadius: 12, background: "#111", marginTop: 12, transform: "scaleX(-1)" }} />
    <button className="primary-button" type="button" onClick={() => void capture()} disabled={!ready || submitting} style={{ marginTop: 16, width: "100%" }}>
      {submitting ? <><LoaderCircle className="spin" size={17} /> 분석 중</> : <><CheckCircle2 size={17} /> {error ? "다시 인증하기" : "지금 인증하기"}</>}
    </button>
  </section></main>;
}
