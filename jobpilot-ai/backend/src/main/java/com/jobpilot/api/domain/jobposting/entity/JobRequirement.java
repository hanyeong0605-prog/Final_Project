package com.jobpilot.api.domain.jobposting.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "job_requirements")
public class JobRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    private String type;
    @Lob @Column(columnDefinition = "TEXT")
    private String content;

    @Lob @Column(name = "source_excerpt", columnDefinition = "TEXT")
    private String sourceExcerpt;

    private String importance;

    @Column(name = "extraction_source")
    private String extractionSource;

    @Column(name = "verification_status")
    private String verificationStatus;

    protected JobRequirement() {}

    public JobRequirement(Long jobPostingId, String type, String content,
                          String sourceExcerpt, String importance,
                          String extractionSource, String verificationStatus) {
        this.jobPostingId = jobPostingId;
        this.type = type;
        this.content = content;
        this.sourceExcerpt = sourceExcerpt;
        this.importance = importance;
        this.extractionSource = extractionSource;
        this.verificationStatus = verificationStatus;
    }

    public Long getId() { return id; }
    public Long getJobPostingId() { return jobPostingId; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getSourceExcerpt() { return sourceExcerpt; }
    public String getImportance() { return importance; }
    public String getExtractionSource() { return extractionSource; }
    public String getVerificationStatus() { return verificationStatus; }
}
