package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(@NotBlank String password) {}
