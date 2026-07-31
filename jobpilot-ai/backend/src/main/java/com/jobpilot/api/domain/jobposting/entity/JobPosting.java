package com.jobpilot.api.domain.jobposting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_postings")
public class JobPosting {
    @Id
    private Long id;

    @Column(name = "company_name")
    private String companyName;

    private String title;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    protected JobPosting() {}

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getTitle() { return title; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
}
