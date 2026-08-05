package com.jobpilot.api.domain.locationjobs.matching.entity;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import jakarta.persistence.*;

@Entity
@Table(name = "job_posting_locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원본 JobPosting 테이블과 1:N / N:1 연관관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @Column(name = "location_text")
    private String locationText;

    private String sido;
    private String sigungu;

    @Column(name = "detailed_address")
    private String detailedAddress;

    @Column(name = "source_provider")
    private String sourceProvider;

    private Double latitude;
    private Double longitude;

    // ================= Getters =================

    public Long getId() {
        return id;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public Long getJobPostingId() {
        return jobPosting != null ? jobPosting.getId() : null;
    }

    public String getLocationText() {
        return locationText;
    }

    public String getSido() {
        return sido;
    }

    public String getSigungu() {
        return sigungu;
    }

    public String getDetailedAddress() {
        return detailedAddress;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}