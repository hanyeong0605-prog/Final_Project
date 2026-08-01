package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_specifications")
public class MemberSpecification {
    @Id @Column(name = "member_id") private Long memberId;
    @Column(name = "education_level") private String educationLevel;
    @Column(name = "school_name") private String schoolName;
    private String major;
    @Column(name = "graduation_status") private String graduationStatus;
    @Column(name = "total_career_months", nullable = false) private int totalCareerMonths;
    @Lob @Column(name = "technical_summary", columnDefinition = "TEXT") private String technicalSummary;
    @Column(name = "portfolio_url", length = 1000) private String portfolioUrl;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected MemberSpecification() {}
    public MemberSpecification(Long memberId) { this.memberId = memberId; }
    public void update(String educationLevel, String schoolName, String major, String graduationStatus,
                       int totalCareerMonths, String technicalSummary, String portfolioUrl) {
        this.educationLevel = educationLevel; this.schoolName = schoolName; this.major = major;
        this.graduationStatus = graduationStatus; this.totalCareerMonths = totalCareerMonths;
        this.technicalSummary = technicalSummary; this.portfolioUrl = portfolioUrl;
        this.updatedAt = LocalDateTime.now();
    }
    public Long getMemberId() { return memberId; }
    public int getTotalCareerMonths() { return totalCareerMonths; }
    public String getTechnicalSummary() { return technicalSummary; }
    public String getEducationLevel() { return educationLevel; }
    public String getSchoolName() { return schoolName; }
    public String getMajor() { return major; }
    public String getGraduationStatus() { return graduationStatus; }
    public String getPortfolioUrl() { return portfolioUrl; }
}
