package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;

public record EmployerLoginRequest(@NotBlank String loginId, @NotBlank String password) {}
