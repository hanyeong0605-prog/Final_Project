package com.jobpilot.api.domain.jobposting.dto;

import java.math.BigDecimal;

public record JobPostingLocationResponse(
        String locationText,
        String sido,
        String sigungu,
        String detailedAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean primaryLocation
) {}
