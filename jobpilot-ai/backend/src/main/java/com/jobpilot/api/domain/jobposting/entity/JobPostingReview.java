package com.jobpilot.api.domain.jobposting.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_posting_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"job_posting_id", "member_id"}))
public class JobPostingReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_posting_id", nullable = false) private Long jobPostingId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private int rating;
    @Column(nullable = false, length = 2000) private String content;
    @Column(name = "employment_verified", nullable = false) private boolean employmentVerified;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected JobPostingReview() {}
    public JobPostingReview(Long jobPostingId, Long memberId, int rating, String content, boolean employmentVerified) {
        this.jobPostingId = jobPostingId; this.memberId = memberId; this.rating = rating; this.content = content;
        this.employmentVerified = employmentVerified; this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    public void update(int rating, String content, boolean employmentVerified) { this.rating = rating; this.content = content; this.employmentVerified = employmentVerified; this.updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; } public Long getMemberId() { return memberId; } public int getRating() { return rating; }
    public String getContent() { return content; } public boolean isEmploymentVerified() { return employmentVerified; } public LocalDateTime getCreatedAt() { return createdAt; }
}
