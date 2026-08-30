package com.jobpilot.api.domain.jobposting.dto;

import java.time.LocalDateTime;

public record JobPostingListResponse(
        Long id,
        String externalJobId,
        String companyName,
        String companyLogoUrl,
        String thumbnailUrl,
        String title,
        String sourceUrl,
        String location,
        String employmentType,
        String experienceType,
        String jobName,
        String salary,
        String keywords,
        LocalDateTime publishedAt,
        LocalDateTime deadlineAt,
        boolean rollingDeadline,
        String status,
        long viewCount,
        long bookmarkCount,
        boolean hasFinancials
) {
    /** Compatibility for list producers that do not join the DART finance tables. */
    public JobPostingListResponse(
            Long id, String externalJobId, String companyName, String companyLogoUrl, String thumbnailUrl,
            String title, String sourceUrl, String location, String employmentType, String experienceType,
            String jobName, String salary, String keywords, LocalDateTime publishedAt, LocalDateTime deadlineAt,
            boolean rollingDeadline, String status, long viewCount, long bookmarkCount
    ) {
        this(id, externalJobId, companyName, companyLogoUrl, thumbnailUrl, title, sourceUrl, location,
                employmentType, experienceType, jobName, salary, keywords, publishedAt, deadlineAt,
                rollingDeadline, status, viewCount, bookmarkCount, false);
    }
}
