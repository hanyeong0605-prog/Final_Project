package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record MemberCareerProfileRequest(
        @NotBlank @Size(max = 80) String targetRole,
        @NotBlank @Size(max = 80) String targetJobFamily,
        List<@Size(max = 100) String> preferredLocations,
        LocalDate availableFrom,
        @NotBlank @Size(max = 30) String experienceType,
        @Size(max = 100) String githubUsername,
        @Size(max = 50) String educationLevel,
        @Size(max = 255) String schoolName,
        @Size(max = 255) String major,
        @Size(max = 30) String graduationStatus,
        @Min(0) int totalCareerMonths,
        @Size(max = 10000) String technicalSummary,
        @Size(max = 1000) String portfolioUrl,
        String profilePhotoDataUrl
) {}
