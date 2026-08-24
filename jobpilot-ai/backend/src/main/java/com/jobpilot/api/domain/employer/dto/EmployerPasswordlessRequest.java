package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;

public record EmployerPasswordlessRequest(@NotBlank String loginId) {}
