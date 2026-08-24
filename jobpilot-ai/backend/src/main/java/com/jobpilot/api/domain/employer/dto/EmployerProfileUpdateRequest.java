package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmployerProfileUpdateRequest(
        @NotBlank String loginId,
        @NotBlank @Email String email,
        String newPassword,
        @NotBlank String managerName,
        String managerPhone,
        @NotBlank String companyName,
        @NotBlank String representativeName,
        @Pattern(regexp = "\\d{8}", message = "개업일자는 YYYYMMDD 8자리로 입력해 주세요.") String openingDate,
        String companyAddress
) {}
