package com.jobpilot.api.domain.matching.dto;

import com.jobpilot.api.domain.matching.policy.MatchGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobMatchSummaryResponse(
        Long jobPostingId,
        String companyName,
        String title,
        String sourceUrl,
        LocalDateTime deadlineAt,
        MatchGrade grade,
        BigDecimal readinessScore,
        String summaryComment
) {}
