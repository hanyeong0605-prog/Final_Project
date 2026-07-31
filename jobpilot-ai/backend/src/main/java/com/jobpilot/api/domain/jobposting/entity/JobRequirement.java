package com.jobpilot.api.domain.jobposting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_requirements")
public class JobRequirement {
    @Id
    private Long id;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    private String type;
    private String content;

    @Column(name = "source_excerpt")
    private String sourceExcerpt;

    protected JobRequirement() {}

    public Long getId() { return id; }
    public Long getJobPostingId() { return jobPostingId; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getSourceExcerpt() { return sourceExcerpt; }
}
