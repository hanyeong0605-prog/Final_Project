package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

public record EmployerEnrollmentRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @AssertTrue(message = "Passwordless 전환 동의가 필요합니다.") boolean passwordlessConsent
) {}
