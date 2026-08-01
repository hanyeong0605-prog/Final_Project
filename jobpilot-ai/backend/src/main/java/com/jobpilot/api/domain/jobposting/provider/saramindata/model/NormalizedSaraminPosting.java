package com.jobpilot.api.domain.jobposting.provider.saramindata.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public record NormalizedSaraminPosting(
        String externalJobId,
        String title,
        String companyName,
        String companyUrl,
        String description,
        String sourceUrl,
        String location,
        String employmentType,
        String experienceType,
        String industryCode,
        String industryName,
        String jobMidCode,
        String jobMidName,
        String jobCode,
        String jobName,
        String salary,
        String keywords,
        LocalDateTime publishedAt,
        LocalDateTime deadlineAt,
        boolean rollingDeadline,
        String status,
        LocalDateTime sourceUpdatedAt,
        String crawlStatus,
        LocalDateTime crawledAt,
        JsonNode rawPayload,
        List<Requirement> requirements
) {
    public record Requirement(String type, String content, String sourceExcerpt, String importance,
                              String extractionSource, String verificationStatus) {}
}
