package com.jobpilot.api.domain.matching.dto;

import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobMatchSummaryResponse(
        Long jobPostingId,
        String companyName,
        String title,
        String sourceUrl,
        String location,
        LocalDateTime deadlineAt,
        RecommendationLevel recommendationLevel,
        BigDecimal readinessScore,
        String summaryComment
) {}
