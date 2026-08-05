package com.jobpilot.api.domain.jobposting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "job_posting_locations")
public class JobPostingLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "location_text")
    private String locationText;

    private String sido;
    private String sigungu;

    @Column(name = "detailed_address")
    private String detailedAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryLocation;

    public Long getJobPostingId() { return jobPostingId; }
    public String getLocationText() { return locationText; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
    public String getDetailedAddress() { return detailedAddress; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public boolean isPrimaryLocation() { return primaryLocation; }
}
