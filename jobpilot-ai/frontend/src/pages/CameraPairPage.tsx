import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Camera, CheckCircle2, LoaderCircle, LogIn, Smartphone } from "lucide-react";
import { useAuth } from "../features/auth/model/AuthContext";
import { joinCameraPairing } from "../features/mock-interview/api/cameraPairingApi";
import { createPeerConnection, openPairingSocket, type PairingSignal } from "../features/mock-interview/lib/cameraPairing";

// PC의 MockInterviewPage stage 값을 그대로 받아오는데, 예전엔 "recording"/"countdown"만
// 구분하고 나머지(예: testing-mic, preparing 같은 시작 전 단계)를 전부 "면접 진행 중"으로
// 뭉뚱그려서, 아직 PC에서 "시작하기"도 안 눌렀는데 폰에는 진행 중이라고 뜨는 문제가 있었다.
function interviewStageLabel(stage: string): string {
  switch (stage) {
    case "start":
    case "device-check":
    case "preparing":
    case "testing-mic":
      return "면접 준비 중 (PC에서 시작하기를 기다리는 중)";
    case "countdown":
      return "곧 질문이 시작됩니다";
    case "get-ready":
      return "질문 확인 중";
    case "recording":
      return "답변 녹화 중";
    case "break":
      return "잠시 휴식 중";
    case "finalizing":
      return "답변 저장 중";
    case "analyzing":
      return "답변 분석 중";
    case "session-report":
      return "면접이 종료되었습니다";
    case "typing":
      return "PC에서 타이핑으로 진행 중";
    default:
      return "면접 진행 중";
  }
}

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
  const pendingCandidatesRef = useRef<RTCIceCandidateInit[]>([]);

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

  const addRemoteCandidate = async (candidate: RTCIceCandidateInit) => {
    const peer = peerRef.current;
    if (!peer || !peer.remoteDescription) {
      pendingCandidatesRef.current.push(candidate);
      return;
    }
    try {
      await peer.addIceCandidate(candidate);
    } catch {
      setError("PC 카메라 연결 후보를 적용하지 못했습니다.");
    }
  };

  const flushPendingCandidates = async (peer: RTCPeerConnection) => {
    const candidates = pendingCandidatesRef.current.splice(0);
    for (const candidate of candidates) await peer.addIceCandidate(candidate);
  };

  async function handleSignal(signal: PairingSignal) {
    if (signal.type === "interview-state") {
      setInterviewState({ stage: signal.stage, question: signal.question, elapsedSec: signal.elapsedSec });
      return;
    }
    if (signal.type !== "offer" || peerRef.current) {
      if (signal.type === "ice-candidate") await addRemoteCandidate(signal.candidate);
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
      await flushPendingCandidates(peer);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      send({ type: "answer", sdp: answer });
      setStatus("연결되었습니다. PC에서 마이크·카메라 확인 후 시작하기를 누르면 면접이 시작됩니다.");
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
        {/* 2026-08-07: 전면(셀피) 카메라라 화면에 보여줄 때만 좌우 반전(CSS transform)한다.
            실제 전송되는 MediaStream 트랙 데이터는 그대로라 PC 쪽 녹화/분석에는 영향 없다 -
            "거울처럼 보이게" 하는 화면 표시용 트릭일 뿐이다.
            video 태그는 스트림이 붙기 전에도 ref 바인딩을 위해 항상 마운트해 둬야 한다
            (streamRef로 조건부 마운트하면 srcObject 대입 시점에 ref가 아직 null이라 영상이
            영영 안 붙는다) - 대신 display로만 숨겨서, 소스 없는 빈 video에 iOS Safari가
            기본으로 그려주는 재생 버튼 아이콘이 스트림 붙기 전까지 안 보이게 한다. */}
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          style={{
            width: "100%",
            borderRadius: 12,
            background: "#111",
            marginTop: 12,
            transform: "scaleX(-1)",
            display: streamRef.current ? "block" : "none",
          }}
        />
        {streamRef.current && <p style={{ color: "#2e9e5b", fontSize: 13 }}><CheckCircle2 size={15} /> PC에 카메라와 마이크를 전송하고 있습니다.</p>}
        {interviewState && (
          <section style={{ marginTop: 16, padding: 14, borderRadius: 12, background: "#f3f5ff", textAlign: "left" }}>
            <strong>PC 면접 진행 상태: {interviewStageLabel(interviewState.stage)}</strong>
            {interviewState.question && <p style={{ margin: "8px 0 0", fontSize: 14 }}>{interviewState.question}</p>}
            {interviewState.stage === "recording" && <p style={{ margin: "8px 0 0", color: "#d84343" }}>● 녹화 중 {interviewState.elapsedSec ?? 0}초</p>}
          </section>
        )}
      </section>
    </main>
  );
}
