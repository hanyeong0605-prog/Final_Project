package com.jobpilot.api.domain.interest.dto;

import jakarta.validation.constraints.NotNull;

public record InterestToggleRequest(
        @NotNull String targetType,
        @NotNull Long targetId,
        boolean interested
) {}
