package com.jobpilot.api.domain.auth.entity;

import com.jobpilot.api.domain.member.entity.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_oauth_accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_subject"}))
public class MemberOAuthAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "member_id", nullable = false) private Member member;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OAuthProvider provider;
    @Column(name = "provider_subject", nullable = false, length = 255) private String providerSubject;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected MemberOAuthAccount() { }
    public MemberOAuthAccount(Member member, OAuthProvider provider, String providerSubject) {
        this.member = member; this.provider = provider; this.providerSubject = providerSubject; this.createdAt = LocalDateTime.now();
    }
    public Member getMember() { return member; }
}
