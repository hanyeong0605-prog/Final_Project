package com.jobpilot.api.domain.interview.pairing;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Single-instance MVP store. Tokens are random, short-lived and removed as soon
 * as the corresponding WebSocket handshake consumes them.
 */
@Service
public class CameraPairingService {
    private static final Duration PAIRING_LIFETIME = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32;

    private final Map<String, PairingSession> rooms = new ConcurrentHashMap<>();
    private final Map<String, SocketTicket> socketTickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CameraPairingService() { this(Clock.systemUTC()); }

    CameraPairingService(Clock clock) { this.clock = clock; }

    public CreatedPairing create(long ownerMemberId) {
        cleanupExpired();
        Instant expiresAt = clock.instant().plus(PAIRING_LIFETIME);
        String roomId = UUID.randomUUID().toString();
        String pairingToken = nextToken();
        rooms.put(roomId, new PairingSession(roomId, ownerMemberId, pairingToken, expiresAt));
        return new CreatedPairing(roomId, pairingToken, issueTicket(roomId, ownerMemberId, PeerRole.PC, expiresAt), expiresAt);
    }

    public JoinedPairing join(long memberId, String roomId, String pairingToken) {
        cleanupExpired();
        PairingSession room = rooms.get(roomId);
        if (room == null || room.expiresAt().isBefore(clock.instant())) throw new PairingException("페어링 시간이 만료되었거나 존재하지 않습니다.");
        if (room.ownerMemberId() != memberId) throw new PairingException("페어링을 만든 계정으로 휴대폰에서 로그인해 주세요.");
        if (!room.pairingToken().equals(pairingToken)) throw new PairingException("유효하지 않은 페어링 코드입니다.");
        // 폰 페이지는 새로고침, 홈 화면 갔다 복귀, 인증 컨텍스트 재렌더 같은 이유로 다시 마운트되면서
        // 같은 QR로 join을 한 번 더 호출한다. 예전엔 여기서 "이미 사용된 코드"라고 막아버려서,
        // WebRTC 영상은 멀쩡히 PC로 전송되는 중인데 폰 화면에만 빨간 에러가 남는 문제가 있었다.
        // 바로 위 ownerMemberId 검사가 "QR을 만든 계정"만 통과시키므로 재접속을 막을 이유가 없다 -
        // 소켓 티켓만 새로 발급하고, 방 자체는 그대로 5분 뒤 만료된다. WebSocket 핸들러도 같은
        // role의 이전 세션을 새 세션으로 교체하도록 이미 되어 있다.
        return new JoinedPairing(roomId, issueTicket(roomId, memberId, PeerRole.PHONE, room.expiresAt()), room.expiresAt());
    }

    public SocketIdentity consumeSocketTicket(String token) {
        cleanupExpired();
        SocketTicket ticket = socketTickets.remove(token);
        if (ticket == null || ticket.expiresAt().isBefore(clock.instant())) throw new PairingException("WebSocket 연결 티켓이 만료되었습니다.");
        PairingSession room = rooms.get(ticket.roomId());
        if (room == null || room.expiresAt().isBefore(clock.instant()) || room.ownerMemberId() != ticket.memberId()) {
            throw new PairingException("유효하지 않은 페어링 세션입니다.");
        }
        return new SocketIdentity(ticket.roomId(), ticket.memberId(), ticket.role());
    }

    private String issueTicket(String roomId, long memberId, PeerRole role, Instant expiresAt) {
        String token = nextToken();
        socketTickets.put(token, new SocketTicket(roomId, memberId, role, expiresAt));
        return token;
    }

    private String nextToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        rooms.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        socketTickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public enum PeerRole { PC, PHONE }
    public record CreatedPairing(String roomId, String pairingToken, String socketTicket, Instant expiresAt) {}
    public record JoinedPairing(String roomId, String socketTicket, Instant expiresAt) {}
    public record SocketIdentity(String roomId, long memberId, PeerRole role) {}
    private record PairingSession(String roomId, long ownerMemberId, String pairingToken, Instant expiresAt) {}
    private record SocketTicket(String roomId, long memberId, PeerRole role, Instant expiresAt) {}
}
