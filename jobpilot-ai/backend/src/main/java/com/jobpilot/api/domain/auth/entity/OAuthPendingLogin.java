package com.jobpilot.api.domain.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_pending_logins", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_subject"}))
public class OAuthPendingLogin {
    // The Flyway schema stores UUID strings as CHAR(36); declare it explicitly
    // so Hibernate validation uses the same MySQL column type.
    @Id @Column(length = 36, columnDefinition = "CHAR(36)") private String id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OAuthProvider provider;
    @Column(name = "provider_subject", nullable = false, length = 255) private String providerSubject;
    @Column(nullable = false, length = 80) private String nickname;
    @Column(name = "provider_email", length = 255) private String providerEmail;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected OAuthPendingLogin() { }
    public OAuthPendingLogin(OAuthProvider provider, String subject, String nickname, String email, LocalDateTime expiresAt) {
        this.id = UUID.randomUUID().toString(); this.provider = provider; this.providerSubject = subject; this.nickname = nickname;
        this.providerEmail = email; this.expiresAt = expiresAt; this.createdAt = LocalDateTime.now();
    }
    public String getId() { return id; }
    public OAuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getNickname() { return nickname; }
    public String getProviderEmail() { return providerEmail; }
    public boolean isUsable() { return usedAt == null && expiresAt.isAfter(LocalDateTime.now()); }
    public void consume() { this.usedAt = LocalDateTime.now(); }
}
