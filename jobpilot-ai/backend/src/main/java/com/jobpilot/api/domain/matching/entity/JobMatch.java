package com.jobpilot.api.domain.matching.entity;

import com.jobpilot.api.domain.matching.policy.MatchGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_matches")
public class JobMatch {
    @Id
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Enumerated(EnumType.STRING)
    private MatchGrade grade;

    private BigDecimal score;

    @Column(name = "summary_comment")
    private String summaryComment;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    protected JobMatch() {}

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getJobPostingId() { return jobPostingId; }
    public MatchGrade getGrade() { return grade; }
    public BigDecimal getScore() { return score; }
    public String getSummaryComment() { return summaryComment; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
}
