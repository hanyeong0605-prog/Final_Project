package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_consents")
public class MemberConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    private ConsentType consentType;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MemberConsent() {}

    public MemberConsent(Member member, ConsentType consentType, String policyVersion, boolean agreed) {
        this.member = member;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.agreed = agreed;
        this.createdAt = LocalDateTime.now();
        this.agreedAt = agreed ? this.createdAt : null;
    }

    public ConsentType getConsentType() {
        return consentType;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public void updateAgreement(boolean agreed) {
        this.agreed = agreed;
        this.agreedAt = agreed ? LocalDateTime.now() : null;
    }
}
