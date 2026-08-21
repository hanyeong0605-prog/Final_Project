package com.jobpilot.api.domain.member.dto;

import java.time.LocalDate;
import java.util.List;

public record MemberCareerProfileResponse(
        String targetRole, String targetJobFamily, List<String> preferredLocations,
        LocalDate availableFrom, String experienceType, String githubUsername,
        String educationLevel, String schoolName, String major, String graduationStatus,
        int totalCareerMonths, String technicalSummary, String portfolioUrl, String profilePhotoDataUrl
) {}
