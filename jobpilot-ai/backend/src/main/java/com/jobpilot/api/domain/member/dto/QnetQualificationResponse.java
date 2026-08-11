package com.jobpilot.api.domain.member.dto;

public record QnetQualificationResponse(
        String code,
        String name,
        String qualificationType,
        String field,
        String subField
) {}
