export type PairingSignal =
  | { type: "peer-ready" | "peer-left" }
  | { type: "offer" | "answer"; sdp: RTCSessionDescriptionInit }
  | { type: "ice-candidate"; candidate: RTCIceCandidateInit }
  | { type: "interview-state"; stage: string; question?: string; elapsedSec?: number };

export function pairingWebSocketUrl(ticket: string) {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/camera-pair?ticket=${encodeURIComponent(ticket)}`;
}

export function openPairingSocket(ticket: string, onSignal: (signal: PairingSignal) => void, onError: (message: string) => void) {
  const socket = new WebSocket(pairingWebSocketUrl(ticket));
  socket.onmessage = (event) => {
    try {
      const signal = JSON.parse(event.data) as PairingSignal;
      if (signal && typeof signal.type === "string") onSignal(signal);
    } catch {
      onError("페어링 신호를 해석하지 못했습니다.");
    }
  };
  socket.onerror = () => onError("페어링 연결에 실패했습니다. HTTPS 주소로 접속했는지 확인해 주세요.");
  return socket;
}

export function createPeerConnection(send: (signal: PairingSignal) => void) {
  const peer = new RTCPeerConnection({
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
  });
  peer.onicecandidate = (event) => {
    if (event.candidate) send({ type: "ice-candidate", candidate: event.candidate.toJSON() });
  };
  return peer;
}
