package com.jobpilot.api.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "verification_token_hash")
    private String verificationTokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected EmailVerification() {}

    public EmailVerification(String email, String codeHash, LocalDateTime expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean wasRequestedRecently(LocalDateTime now, long cooldownSeconds) {
        return createdAt.plusSeconds(cooldownSeconds).isAfter(now);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public boolean matchesCode(String code, PasswordEncoder passwordEncoder) {
        return passwordEncoder.matches(code, codeHash);
    }

    public void recordFailedAttempt() {
        failedAttempts++;
    }

    public void verify(String verificationTokenHash) {
        this.verificationTokenHash = verificationTokenHash;
        this.verifiedAt = LocalDateTime.now();
    }

    public boolean matchesVerificationToken(String verificationToken, PasswordEncoder passwordEncoder) {
        return verificationTokenHash != null && passwordEncoder.matches(verificationToken, verificationTokenHash);
    }

    public void consume() {
        this.consumedAt = LocalDateTime.now();
    }
}
