package com.jobpilot.api.domain.interview.pairing;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Relays SDP/ICE payloads only; camera and microphone media never traverse this server. */
@Component
public class CameraPairingWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CameraPairingWebSocketHandler.class);
    private static final int MAX_SIGNAL_BYTES = 64 * 1024;
    private final Map<String, Map<CameraPairingService.PeerRole, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        CameraPairingService.SocketIdentity identity = identity(session);
        log.info("Camera pairing socket connected: room={}, role={}", identity.roomId(), identity.role());
        Map<CameraPairingService.PeerRole, WebSocketSession> peers = rooms.computeIfAbsent(identity.roomId(), ignored -> new ConcurrentHashMap<>());
        WebSocketSession previous = peers.put(identity.role(), session);
        if (previous != null && previous.isOpen()) previous.close(CloseStatus.NORMAL.withReason("새 기기가 연결되었습니다."));
        WebSocketSession peer = peers.get(opposite(identity.role()));
        if (peer != null && peer.isOpen()) {
            peer.sendMessage(new TextMessage("{\"type\":\"peer-ready\"}"));
            session.sendMessage(new TextMessage("{\"type\":\"peer-ready\"}"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (message.getPayloadLength() > MAX_SIGNAL_BYTES) {
            session.close(new CloseStatus(1009, "시그널 메시지가 너무 큽니다."));
            return;
        }
        CameraPairingService.SocketIdentity identity = identity(session);
        log.debug("Camera pairing signal relayed: room={}, role={}, bytes={}", identity.roomId(), identity.role(), message.getPayloadLength());
        Map<CameraPairingService.PeerRole, WebSocketSession> peers = rooms.get(identity.roomId());
        if (peers != null) notifyPeer(peers, identity.role(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException {
        CameraPairingService.SocketIdentity identity = identity(session);
        log.info("Camera pairing socket closed: room={}, role={}, code={}, reason={}", identity.roomId(), identity.role(), status.getCode(), status.getReason());
        Map<CameraPairingService.PeerRole, WebSocketSession> peers = rooms.get(identity.roomId());
        if (peers == null) return;
        peers.remove(identity.role(), session);
        notifyPeer(peers, identity.role(), "{\"type\":\"peer-left\"}");
        if (peers.isEmpty()) rooms.remove(identity.roomId(), peers);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        CameraPairingService.SocketIdentity identity = identity(session);
        log.warn("Camera pairing socket transport error: room={}, role={}", identity.roomId(), identity.role(), exception);
    }

    private void notifyPeer(Map<CameraPairingService.PeerRole, WebSocketSession> peers, CameraPairingService.PeerRole sender, String payload) throws IOException {
        CameraPairingService.PeerRole recipient = opposite(sender);
        WebSocketSession peer = peers.get(recipient);
        if (peer != null && peer.isOpen()) peer.sendMessage(new TextMessage(payload));
    }

    private CameraPairingService.SocketIdentity identity(WebSocketSession session) {
        return (CameraPairingService.SocketIdentity) session.getAttributes().get(CameraPairingWebSocketTicketInterceptor.IDENTITY_ATTRIBUTE);
    }

    private CameraPairingService.PeerRole opposite(CameraPairingService.PeerRole role) {
        return role == CameraPairingService.PeerRole.PC ? CameraPairingService.PeerRole.PHONE : CameraPairingService.PeerRole.PC;
    }
}
