package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "certificates")
public class Certificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private String name;
    private String issuer;
    @Column(name = "acquired_at") private LocalDate acquiredAt;
    @Column(name = "expires_at") private LocalDate expiresAt;
    @Column(name = "official_url", length = 1000) private String officialUrl;
    protected Certificate() {}

    public Certificate(Long memberId, String name, String issuer, LocalDate acquiredAt,
                       LocalDate expiresAt, String officialUrl) {
        this.memberId = memberId;
        this.name = name;
        this.issuer = issuer;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
        this.officialUrl = officialUrl;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getName() { return name; }
    public String getIssuer() { return issuer; }
    public LocalDate getAcquiredAt() { return acquiredAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public String getOfficialUrl() { return officialUrl; }
}
