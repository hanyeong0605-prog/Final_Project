package com.jobpilot.api.domain.member.dto;

public record EducationMajorResponse(
        String id,
        String name,
        String field,
        String relatedNames
) {}
