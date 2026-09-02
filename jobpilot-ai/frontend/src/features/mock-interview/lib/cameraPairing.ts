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
  // close()를 코드 없이 부르면 close 이벤트 코드가 1005(No Status Rcvd)로 들어온다.
  // 대부분 effect 정리 과정에서 우리가 스스로 닫은 경우라 에러로 띄우면 안 된다.
  socket.onclose = (event) => {
    if (event.code !== 1000 && event.code !== 1005) {
      onError(`페어링 WebSocket이 종료되었습니다. 코드: ${event.code}${event.reason ? ` (${event.reason})` : ""}`);
    }
  };
  return socket;
}

export function createPeerConnection(send: (signal: PairingSignal) => void) {
  const peer = new RTCPeerConnection({
    // The phone camera is intentionally a same-Wi-Fi feature. Keep media
    // direct between the phone and PC; the WebSocket only exchanges signals.
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
  });
  peer.onicecandidate = (event) => {
    if (event.candidate) send({ type: "ice-candidate", candidate: event.candidate.toJSON() });
  };
  return peer;
}
