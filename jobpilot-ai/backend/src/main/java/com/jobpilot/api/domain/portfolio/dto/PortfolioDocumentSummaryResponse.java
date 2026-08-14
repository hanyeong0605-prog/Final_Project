package com.jobpilot.api.domain.portfolio.dto;

import java.time.LocalDateTime;

public record PortfolioDocumentSummaryResponse(
        Long id,
        String repositoryFullName,
        String repositoryUrl,
        String title,
        String narrativeSource,
        String template,
        boolean hasPptx,
        boolean hasPdf,
        LocalDateTime createdAt
) {
}
