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
 * Single-instance MVP store. The QR pairing token is random and short-lived.
 * WebSocket tickets are single-use, but the same authenticated owner may scan
 * the QR again during its lifetime to recover from a mobile-page reload.
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
        // The phone page can be re-mounted after login or a network retry.
        // Authentication already verifies that only the PC owner can join, so
        // reusing a still-valid QR is safe and avoids a false 400 on retry.
        if (!room.pairingToken().equals(pairingToken)) throw new PairingException("유효하지 않은 페어링 코드입니다.");
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
