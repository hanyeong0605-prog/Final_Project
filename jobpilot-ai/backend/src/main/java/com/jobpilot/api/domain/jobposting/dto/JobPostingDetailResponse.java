package com.jobpilot.api.domain.jobposting.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobPostingDetailResponse(
        Long id,
        String externalJobId,
        String sourceProvider,
        String companyName,
        String companyUrl,
        String companyLogoUrl,
        String title,
        String description,
        String sourceUrl,
        String location,
        String employmentType,
        String experienceType,
        Boolean entryLevel,
        String industryName,
        String jobMidName,
        String jobName,
        String salary,
        String keywords,
        LocalDateTime publishedAt,
        LocalDateTime deadlineAt,
        boolean rollingDeadline,
        String status,
        List<JobPostingLocationResponse> locations,
        List<String> imageUrls,
        long viewCount,
        long bookmarkCount
) {}
