package com.jobpilot.api.domain.jobposting.dto;

public record JobPostingLocationResponse(
        String locationText,
        String sido,
        String sigungu,
        String detailedAddress,
        Double latitude,
        Double longitude,
        boolean primaryLocation
) {}
