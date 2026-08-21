package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;

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
    @Lob @Column(name = "profile_photo", columnDefinition = "LONGBLOB") private byte[] profilePhoto;
    @Column(name = "profile_photo_content_type", length = 40) private String profilePhotoContentType;
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
    // 2026-08-10: 태스크 #63 "반영" 방향 - 이력서(자기소개서/프로젝트) 저장 시 기술 요약만
    // 새로 합성해서 반영한다. update()는 온보딩 폼 전체 제출용이라 다른 필드까지 같이
    // 덮어써버리므로, 이 용도로는 technicalSummary 하나만 건드리는 좁은 메서드가 필요하다.
    public void updateTechnicalSummary(String technicalSummary) {
        this.technicalSummary = technicalSummary;
        this.updatedAt = LocalDateTime.now();
    }
    public void updateProfilePhoto(byte[] bytes, String contentType) {
        this.profilePhoto = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
        this.profilePhotoContentType = bytes == null ? null : contentType;
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
    public byte[] getProfilePhoto() { return profilePhoto == null ? null : Arrays.copyOf(profilePhoto, profilePhoto.length); }
    public String getProfilePhotoContentType() { return profilePhotoContentType; }
}
