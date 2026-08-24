package com.jobpilot.api.domain.employer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employer_notifications")
public class EmployerNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "employer_account_id", nullable = false) private Long employerAccountId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "job_posting_id", nullable = false) private Long jobPostingId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String body;
    @Column(nullable = false) private String url;
    @Column(name = "is_read", nullable = false) private boolean read;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected EmployerNotification() {}
    public EmployerNotification(Long employerAccountId, Long memberId, Long jobPostingId, String title, String body) {
        this.employerAccountId = employerAccountId; this.memberId = memberId; this.jobPostingId = jobPostingId;
        this.title = title; this.body = body; this.url = "/employer/postings"; this.createdAt = LocalDateTime.now();
    }
    public void markRead() { this.read = true; }
    public Long getId() { return id; } public Long getEmployerAccountId() { return employerAccountId; }
    public String getTitle() { return title; } public String getBody() { return body; } public String getUrl() { return url; }
    public boolean isRead() { return read; } public LocalDateTime getCreatedAt() { return createdAt; }
}
