package com.jobpilot.api.domain.employer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 2026-08-19: 기업회원(구인 기업) 계정. Member(구직자/관리자)와는 완전히 분리된 테이블 -
 * V32__employer_accounts.sql 주석 참고. 담당자 1명이 회사를 대표해서 가입하는 구조라
 * 담당자 정보와 사업자 정보를 한 행에 같이 둔다.
 */
@Entity
@Table(name = "employer_accounts")
public class EmployerAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "manager_name", nullable = false)
    private String managerName;

    @Column(name = "manager_phone")
    private String managerPhone;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "business_registration_number", nullable = false, unique = true, length = 12)
    private String businessRegistrationNumber;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Column(name = "opening_date", nullable = false, length = 8)
    private String openingDate;

    @Column(name = "company_address")
    private String companyAddress;

    @Column(name = "nts_verified", nullable = false)
    private boolean ntsVerified;

    @Column(name = "nts_checked_at")
    private LocalDateTime ntsCheckedAt;

    @Lob
    @Column(name = "nts_raw_response", columnDefinition = "TEXT")
    private String ntsRawResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployerAccountStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EmployerAccount() {}

    public EmployerAccount(String loginId, String email, String passwordHash, String managerName, String managerPhone,
                            String companyName, String businessRegistrationNumber, String representativeName,
                            String openingDate, String companyAddress) {
        this.loginId = loginId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.managerName = managerName;
        this.managerPhone = managerPhone;
        this.companyName = companyName;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.representativeName = representativeName;
        this.openingDate = openingDate;
        this.companyAddress = companyAddress;
        this.ntsVerified = false;
        this.status = EmployerAccountStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void applyNtsVerificationResult(boolean verified, String rawResponse) {
        this.ntsVerified = verified;
        this.ntsCheckedAt = LocalDateTime.now();
        this.ntsRawResponse = rawResponse;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(Long adminMemberId) {
        this.status = EmployerAccountStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedBy = adminMemberId;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = this.reviewedAt;
    }

    public void reject(Long adminMemberId, String reason) {
        this.status = EmployerAccountStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedBy = adminMemberId;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = this.reviewedAt;
    }

    public boolean isApproved() { return status == EmployerAccountStatus.APPROVED; }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getManagerName() { return managerName; }
    public String getManagerPhone() { return managerPhone; }
    public String getCompanyName() { return companyName; }
    public String getBusinessRegistrationNumber() { return businessRegistrationNumber; }
    public String getRepresentativeName() { return representativeName; }
    public String getOpeningDate() { return openingDate; }
    public String getCompanyAddress() { return companyAddress; }
    public boolean isNtsVerified() { return ntsVerified; }
    public LocalDateTime getNtsCheckedAt() { return ntsCheckedAt; }
    public String getNtsRawResponse() { return ntsRawResponse; }
    public EmployerAccountStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Long getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
