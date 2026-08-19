package com.jobpilot.api.domain.resume.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record ResumeSaveStateRequest(@NotNull @Pattern(regexp = "DRAFT|SAVED") String status) {}
