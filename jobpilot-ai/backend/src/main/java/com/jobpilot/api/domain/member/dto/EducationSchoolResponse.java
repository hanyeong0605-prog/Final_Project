package com.jobpilot.api.domain.member.dto;

public record EducationSchoolResponse(
        String id,
        String name,
        String schoolType,
        String region,
        String campusName
) {}
