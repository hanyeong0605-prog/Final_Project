package com.jobpilot.api.domain.admin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Short-lived, single-use bridge between an administrator's desktop and phone. */
@Service
public class AdminFacePairingService {
    private static final Duration LIFETIME = Duration.ofMinutes(2);
    /** A face-verified browser session stays valid until logout, browser close, or this safety limit. */
    private static final Duration VERIFIED_LIFETIME = Duration.ofHours(8);
    private static final int TOKEN_BYTES = 32;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public AdminFacePairingService() { this(Clock.systemUTC()); }
    AdminFacePairingService(Clock clock) { this.clock = clock; }

    public Created create(long memberId) {
        cleanup();
        String id = UUID.randomUUID().toString();
        String token = token();
        Instant expiresAt = clock.instant().plus(LIFETIME);
        sessions.put(id, new Session(memberId, token, expiresAt, Status.WAITING, null, null));
        return new Created(id, token, expiresAt);
    }

    public void verifyOwner(long memberId, String id, String token) {
        Session session = active(id);
        if (session.memberId() != memberId || !session.token().equals(token)) throw new AdminFacePairingException("유효하지 않은 휴대폰 인증 코드입니다.");
        // A failed comparison must not invalidate the QR immediately. The phone
        // stays on the capture page, so the same signed-in administrator can
        // correct framing or lighting and try again until the short expiry.
        if (session.status() != Status.WAITING && session.status() != Status.REJECTED) {
            throw new AdminFacePairingException("이미 완료된 인증 코드입니다. PC에서 새 QR 코드를 생성해 주세요.");
        }
    }

    public void complete(long memberId, String id, boolean verified, double similarity, String message) {
        Session session = active(id);
        if (session.memberId() != memberId) throw new AdminFacePairingException("인증 세션의 소유자가 아닙니다.");
        Instant expiresAt = verified ? clock.instant().plus(VERIFIED_LIFETIME) : session.expiresAt();
        sessions.put(id, new Session(memberId, session.token(), expiresAt, verified ? Status.VERIFIED : Status.REJECTED, similarity, message));
    }

    public Result result(long memberId, String id) {
        Session session = active(id);
        if (session.memberId() != memberId) throw new AdminFacePairingException("인증 세션의 소유자가 아닙니다.");
        return new Result(session.status(), session.similarity(), session.message(), session.expiresAt());
    }

    /**
     * Checks the proof sent by the desktop after the phone has completed face verification.
     * The proof is tied to the authenticated member, short-lived, and cannot be created by
     * merely possessing an administrator JWT.
     */
    public boolean isVerified(long memberId, String id) {
        if (id == null || id.isBlank()) return false;
        cleanup();
        Session session = sessions.get(id);
        return session != null
                && session.memberId() == memberId
                && session.status() == Status.VERIFIED
                && !session.expiresAt().isBefore(clock.instant());
    }

    private Session active(String id) {
        cleanup();
        Session session = sessions.get(id);
        if (session == null || session.expiresAt().isBefore(clock.instant())) throw new AdminFacePairingException("인증 시간이 만료되었거나 존재하지 않습니다.");
        return session;
    }
    private String token() { byte[] bytes = new byte[TOKEN_BYTES]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private void cleanup() { Instant now = clock.instant(); sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now)); }

    public enum Status { WAITING, VERIFIED, REJECTED }
    public record Created(String sessionId, String token, Instant expiresAt) {}
    public record Result(Status status, Double similarity, String message, Instant expiresAt) {}
    private record Session(long memberId, String token, Instant expiresAt, Status status, Double similarity, String message) {}
}
