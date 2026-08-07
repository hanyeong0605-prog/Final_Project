import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Camera, CheckCircle2, LoaderCircle, LogIn, Smartphone } from "lucide-react";
import { useAuth } from "../features/auth/model/AuthContext";
import { joinCameraPairing } from "../features/mock-interview/api/cameraPairingApi";
import { createPeerConnection, openPairingSocket, type PairingSignal } from "../features/mock-interview/lib/cameraPairing";

export function CameraPairPage() {
  const { member, loading } = useAuth();
  const [params] = useSearchParams();
  const roomId = params.get("room") ?? "";
  const pairingToken = params.get("token") ?? "";
  const loginHref = `/login?returnTo=${encodeURIComponent(`${window.location.pathname}${window.location.search}`)}`;
  const [status, setStatus] = useState("같은 계정인지 확인하고 있습니다.");
  const [error, setError] = useState<string | null>(null);
  const [interviewState, setInterviewState] = useState<{ stage: string; question?: string; elapsedSec?: number } | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    if (!member || !roomId || !pairingToken) return;
    let disposed = false;
    joinCameraPairing(roomId, pairingToken)
      .then((joined) => {
        if (disposed) return;
        setStatus("PC의 면접 시작 신호를 기다리고 있습니다.");
        socketRef.current = openPairingSocket(joined.socketTicket, handleSignal, setError);
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "페어링에 실패했습니다."));
    return () => {
      disposed = true;
      socketRef.current?.close();
      peerRef.current?.close();
      streamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, [member, roomId, pairingToken]);

  const send = (signal: PairingSignal) => {
    if (socketRef.current?.readyState === WebSocket.OPEN) socketRef.current.send(JSON.stringify(signal));
  };

  async function handleSignal(signal: PairingSignal) {
    if (signal.type === "interview-state") {
      setInterviewState({ stage: signal.stage, question: signal.question, elapsedSec: signal.elapsedSec });
      return;
    }
    if (signal.type !== "offer" || peerRef.current) {
      if (signal.type === "ice-candidate" && peerRef.current) await peerRef.current.addIceCandidate(signal.candidate);
      return;
    }
    try {
      setStatus("카메라와 마이크 권한을 요청하고 있습니다.");
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { width: 1280, height: 720 } });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
      const peer = createPeerConnection(send);
      peerRef.current = peer;
      peer.onconnectionstatechange = () => {
        if (["closed", "failed", "disconnected"].includes(peer.connectionState)) {
          streamRef.current?.getTracks().forEach((track) => track.stop());
          setStatus("PC와의 연결이 종료되었습니다.");
        }
      };
      stream.getTracks().forEach((track) => peer.addTrack(track, stream));
      await peer.setRemoteDescription(signal.sdp);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      send({ type: "answer", sdp: answer });
      setStatus("연결되었습니다. PC와 같은 면접이 자동으로 시작됩니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "카메라 또는 마이크 권한을 얻지 못했습니다.");
    }
  }

  if (loading) return <div className="auth-loading">로그인 상태를 확인하고 있습니다.</div>;
  if (!roomId || !pairingToken) return <main className="auth-page"><section className="auth-card"><h1>잘못된 QR 코드입니다.</h1><p>PC에서 QR 코드를 다시 생성해 주세요.</p></section></main>;
  if (!member) return <main className="auth-page"><section className="auth-card"><Smartphone size={32} /><h1>같은 계정으로 로그인해 주세요</h1><p>로그인 후 QR 페어링 화면으로 자동 복귀합니다.</p><Link className="primary-button" to={loginHref}><LogIn size={16} /> 로그인</Link></section></main>;

  return (
    <main className="auth-page">
      <section className="auth-card" style={{ maxWidth: 520 }}>
        <div className="brand-mark"><Camera size={22} /></div>
        <h1>휴대폰 카메라 면접</h1>
        <p>{status}</p>
        {error && <p style={{ color: "#c0392b" }}>{error}</p>}
        {!streamRef.current && !error && <LoaderCircle className="spin" size={26} />}
        <video ref={videoRef} autoPlay muted playsInline style={{ width: "100%", borderRadius: 12, background: "#111", marginTop: 12 }} />
        {streamRef.current && <p style={{ color: "#2e9e5b", fontSize: 13 }}><CheckCircle2 size={15} /> PC에 카메라와 마이크를 전송하고 있습니다.</p>}
        {interviewState && (
          <section style={{ marginTop: 16, padding: 14, borderRadius: 12, background: "#f3f5ff", textAlign: "left" }}>
            <strong>PC 면접 진행 상태: {interviewState.stage === "recording" ? "답변 녹화 중" : interviewState.stage === "countdown" ? "곧 질문이 시작됩니다" : "면접 진행 중"}</strong>
            {interviewState.question && <p style={{ margin: "8px 0 0", fontSize: 14 }}>{interviewState.question}</p>}
            {interviewState.stage === "recording" && <p style={{ margin: "8px 0 0", color: "#d84343" }}>● 녹화 중 {interviewState.elapsedSec ?? 0}초</p>}
          </section>
        )}
      </section>
    </main>
  );
}
