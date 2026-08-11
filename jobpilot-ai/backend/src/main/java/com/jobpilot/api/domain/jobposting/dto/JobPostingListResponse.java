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
        long bookmarkCount
) {}
