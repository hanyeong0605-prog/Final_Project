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
  const [status, setStatus] = useState("같은 계정인지 확인하는 중입니다.");
  const [error, setError] = useState<string | null>(null);
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
        setStatus("PC 연결을 기다리는 중입니다.");
        socketRef.current = openPairingSocket(joined.socketTicket, handleSignal, setError);
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "휴대폰 페어링에 실패했습니다."));
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
    if (signal.type !== "offer" || peerRef.current) {
      if (signal.type === "ice-candidate" && peerRef.current) await peerRef.current.addIceCandidate(signal.candidate);
      return;
    }
    try {
      setStatus("카메라와 마이크 권한을 요청하는 중입니다.");
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { width: 1280, height: 720 } });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
      const peer = createPeerConnection(send);
      peerRef.current = peer;
      peer.onconnectionstatechange = () => {
        if (["closed", "failed", "disconnected"].includes(peer.connectionState)) {
          streamRef.current?.getTracks().forEach((track) => track.stop());
          setStatus("PC 연결이 종료되었습니다.");
        }
      };
      stream.getTracks().forEach((track) => peer.addTrack(track, stream));
      await peer.setRemoteDescription(signal.sdp);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      send({ type: "answer", sdp: answer });
      setStatus("연결 완료. 휴대폰은 켜 둔 채 PC에서 면접을 시작하세요.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "카메라 또는 마이크 권한을 얻지 못했습니다.");
    }
  }

  if (loading) return <div className="auth-loading">로그인 상태를 확인하는 중입니다.</div>;
  if (!roomId || !pairingToken) return <main className="auth-page"><section className="auth-card"><h1>잘못된 QR 코드입니다.</h1><p>PC에서 새 QR 코드를 생성한 뒤 다시 스캔해 주세요.</p></section></main>;
  if (!member) return <main className="auth-page"><section className="auth-card"><Smartphone size={32} /><h1>같은 계정으로 로그인해 주세요.</h1><p>로그인 후 QR을 다시 스캔하면 휴대폰 카메라를 PC 면접에 연결할 수 있습니다.</p><Link className="primary-button" to="/login"><LogIn size={16} /> 로그인</Link></section></main>;

  return (
    <main className="auth-page">
      <section className="auth-card" style={{ maxWidth: 520 }}>
        <div className="brand-mark"><Camera size={22} /></div>
        <h1>휴대폰 카메라 연결</h1>
        <p>{status}</p>
        {error && <p style={{ color: "#c0392b" }}>{error}</p>}
        {!streamRef.current && !error && <LoaderCircle className="spin" size={26} />}
        <video ref={videoRef} autoPlay muted playsInline style={{ width: "100%", borderRadius: 12, background: "#111", marginTop: 12 }} />
        {streamRef.current && <p style={{ color: "#2e9e5b", fontSize: 13 }}><CheckCircle2 size={15} /> PC에 영상을 보내고 있습니다.</p>}
      </section>
    </main>
  );
}
