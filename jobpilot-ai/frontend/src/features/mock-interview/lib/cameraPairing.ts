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
  socket.onerror = () => onError("페어링 WebSocket 연결 중 오류가 발생했습니다.");
  socket.onclose = (event) => {
    if (event.code !== 1000) {
      onError(`페어링 WebSocket이 종료되었습니다. 코드: ${event.code}${event.reason ? ` (${event.reason})` : ""}`);
    }
  };
  return socket;
}

export function createPeerConnection(send: (signal: PairingSignal) => void) {
  const turnUrls = (import.meta.env.VITE_TURN_URL ?? "")
    .split(",")
    .map((url: string) => url.trim())
    .filter(Boolean);
  const turnUsername = import.meta.env.VITE_TURN_USERNAME?.trim();
  const turnCredential = import.meta.env.VITE_TURN_CREDENTIAL?.trim();
  const iceServers: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];
  // TURN relays media when the PC and phone are on different mobile/Wi-Fi
  // networks or either side is behind a restrictive NAT. It is optional in
  // local development, but production supplies these three VITE_* values.
  if (turnUrls.length > 0 && turnUsername && turnCredential) {
    iceServers.push({ urls: turnUrls, username: turnUsername, credential: turnCredential });
  }
  const peer = new RTCPeerConnection({
    iceServers,
  });
  peer.onicecandidate = (event) => {
    if (event.candidate) send({ type: "ice-candidate", candidate: event.candidate.toJSON() });
  };
  return peer;
}
