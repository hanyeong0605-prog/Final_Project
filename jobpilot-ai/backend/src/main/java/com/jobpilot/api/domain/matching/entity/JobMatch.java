package com.jobpilot.api.domain.matching.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_matches")
public class JobMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "job_posting_id")
    private Long jobPostingId;

    @Column(name = "self_introduction_id")
    private Long selfIntroductionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_level")
    private RecommendationLevel recommendationLevel;

    @Column(name = "readiness_score")
    private BigDecimal readinessScore;

    @jakarta.persistence.Lob
    @Column(name = "summary_comment", columnDefinition = "TEXT")
    private String summaryComment;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "missing_required_count")
    private int missingRequiredCount;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "profile_snapshot", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode profileSnapshot;

    protected JobMatch() {}

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getJobPostingId() { return jobPostingId; }
    public Long getSelfIntroductionId() { return selfIntroductionId; }
    public RecommendationLevel getRecommendationLevel() { return recommendationLevel; }
    public BigDecimal getReadinessScore() { return readinessScore; }
    public String getSummaryComment() { return summaryComment; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public int getMissingRequiredCount() { return missingRequiredCount; }
}
