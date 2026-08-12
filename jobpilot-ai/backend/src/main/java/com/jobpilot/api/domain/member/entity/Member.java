package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "login_id", nullable = false, unique = true) private String loginId;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private String nickname;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "onboarding_completed", nullable = false) private boolean onboardingCompleted;
    protected Member() {}
    public Member(String loginId, String email, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = MemberRole.USER;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.onboardingCompleted = false;
    }
    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public MemberRole getRole() { return role; }
    public boolean isAdmin() { return role == MemberRole.ADMIN; }
    public void changeNickname(String nickname) { this.nickname = nickname; this.updatedAt = LocalDateTime.now(); }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; this.updatedAt = LocalDateTime.now(); }
    public void completeOnboarding() { this.onboardingCompleted = true; this.updatedAt = LocalDateTime.now(); }
    public void changeRole(MemberRole role) { this.role = role; this.updatedAt = LocalDateTime.now(); }
}
