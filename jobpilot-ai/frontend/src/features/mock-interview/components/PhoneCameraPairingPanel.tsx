import { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Camera, LoaderCircle, Smartphone, X } from "lucide-react";
import { createCameraPairing, type CameraPairing } from "../api/cameraPairingApi";
import { createPeerConnection, openPairingSocket, type PairingSignal } from "../lib/cameraPairing";

type Props = {
  onRemoteStream: (stream: MediaStream) => void;
  onConnected: (connection: { disconnect: () => void; sendInterviewState: (stage: string, question?: string, elapsedSec?: number) => void }) => void;
  onClose: () => void;
};

export function PhoneCameraPairingPanel({ onRemoteStream, onConnected, onClose }: Props) {
  const [pairing, setPairing] = useState<CameraPairing | null>(null);
  const [status, setStatus] = useState("QR 코드를 생성하는 중입니다.");
  const [error, setError] = useState<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const peerRef = useRef<RTCPeerConnection | null>(null);
  const transferredRef = useRef(false);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const pendingCandidatesRef = useRef<RTCIceCandidateInit[]>([]);

  useEffect(() => {
    let disposed = false;
    createCameraPairing()
      .then((created) => {
        if (disposed) return;
        setPairing(created);
        setStatus("PC와 휴대폰을 같은 Wi-Fi에 연결한 뒤, 같은 계정으로 QR을 스캔해 주세요.");
        socketRef.current = openPairingSocket(created.socketTicket, handleSignal, setError);
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "QR 코드를 만들지 못했습니다."));
    return () => {
      disposed = true;
      // Once the stream is handed to MockInterviewPage, that page owns the
      // peer connection for the entire interview. Closing it here would make
      // React stage changes immediately stop the phone camera.
      if (!transferredRef.current) {
        socketRef.current?.close();
        peerRef.current?.close();
      }
    };
  }, []);

  const send = (signal: PairingSignal) => {
    if (socketRef.current?.readyState === WebSocket.OPEN) socketRef.current.send(JSON.stringify(signal));
  };

  function handleSignal(signal: PairingSignal) {
    if (signal.type === "peer-ready") {
      void offerPhoneConnection();
      return;
    }
    if (signal.type === "answer" && peerRef.current) {
      void peerRef.current.setRemoteDescription(signal.sdp)
        .then(async () => {
          await flushPendingCandidates(peerRef.current!);
          setStatus("휴대폰 카메라에 연결되었습니다.");
        })
        .catch(() => setError("휴대폰 카메라 연결 정보를 적용하지 못했습니다."));
      return;
    }
    if (signal.type === "ice-candidate") void addRemoteCandidate(signal.candidate);
    if (signal.type === "peer-left") setStatus("휴대폰 연결이 종료되었습니다.");
  }

  async function addRemoteCandidate(candidate: RTCIceCandidateInit) {
    const peer = peerRef.current;
    if (!peer || !peer.remoteDescription) {
      pendingCandidatesRef.current.push(candidate);
      return;
    }
    try {
      await peer.addIceCandidate(candidate);
    } catch {
      setError("휴대폰 카메라 네트워크 후보를 연결하지 못했습니다.");
    }
  }

  async function flushPendingCandidates(peer: RTCPeerConnection) {
    const candidates = pendingCandidatesRef.current.splice(0);
    for (const candidate of candidates) await peer.addIceCandidate(candidate);
  }

  async function offerPhoneConnection() {
    if (peerRef.current) return;
    const peer = createPeerConnection(send);
    peerRef.current = peer;
    peer.addTransceiver("video", { direction: "recvonly" });
    peer.addTransceiver("audio", { direction: "recvonly" });
    const handOffWhenVideoArrives = () => {
      const stream = remoteStreamRef.current;
      if (!stream || transferredRef.current) return;
      if (stream.getVideoTracks().length === 0) return;
      transferredRef.current = true;
      onConnected({
        disconnect: () => {
          socketRef.current?.close();
          peer.close();
        },
        sendInterviewState: (stage, question, elapsedSec) => {
          send({ type: "interview-state", stage, question, elapsedSec });
        },
      });
      onRemoteStream(stream);
    };
    peer.ontrack = (event) => {
      const stream = remoteStreamRef.current ?? event.streams[0] ?? new MediaStream();
      if (!stream.getTracks().includes(event.track)) stream.addTrack(event.track);
      remoteStreamRef.current = stream;
      if (event.track.kind === "video") {
        // Safari can omit `unmute` for remote tracks. `ontrack` itself means
        // the video receiver is available, so hand it to the PC immediately.
        event.track.addEventListener("unmute", handOffWhenVideoArrives, { once: true });
        handOffWhenVideoArrives();
        window.setTimeout(handOffWhenVideoArrives, 350);
      }
    };
    peer.onconnectionstatechange = () => {
      if (peer.connectionState === "connected") window.setTimeout(handOffWhenVideoArrives, 350);
    };
    const offer = await peer.createOffer();
    await peer.setLocalDescription(offer);
    send({ type: "offer", sdp: offer });
    setStatus("휴대폰의 카메라·마이크 권한을 기다리는 중입니다.");
  }

  const pairUrl = pairing
    ? `${window.location.origin}/camera-pair?room=${encodeURIComponent(pairing.roomId)}&token=${encodeURIComponent(pairing.pairingToken)}`
    : "";

  return (
    <div style={{ width: "100%", maxWidth: 480, border: "1px solid #dfe4ec", borderRadius: 14, padding: 18, background: "#fbfcff" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
        <div><strong style={{ fontSize: 16 }}><Smartphone size={19} /> 폰을 카메라로 연결</strong><p style={{ margin: "7px 0 0", fontSize: 14, color: "#667085" }}>{status}</p></div>
        <button className="text-button" onClick={onClose} type="button"><X size={16} /> 닫기</button>
      </div>
      {error && <p style={{ color: "#c0392b", fontSize: 14 }}>{error}</p>}
      {!pairing && !error && <LoaderCircle className="spin" size={28} style={{ display: "block", margin: "22px auto" }} />}
      {pairing && (
        <div style={{ display: "grid", justifyItems: "center", gap: 12, marginTop: 16 }}>
          <QRCodeSVG value={pairUrl} size={210} level="M" includeMargin />
          <span style={{ fontSize: 14, color: "#667085" }}><Camera size={15} /> PC와 휴대폰은 반드시 같은 Wi-Fi에 연결해야 합니다.</span>
          <span style={{ fontSize: 14, color: "#667085" }}>QR은 5분 안에 한 번만 사용할 수 있습니다.</span>
        </div>
      )}
    </div>
  );
}
