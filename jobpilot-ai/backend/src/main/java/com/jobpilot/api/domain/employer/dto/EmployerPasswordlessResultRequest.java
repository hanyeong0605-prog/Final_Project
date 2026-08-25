package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;

public record EmployerPasswordlessResultRequest(@NotBlank String loginId, @NotBlank String sessionId) {}
