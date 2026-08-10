package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MemberCertificateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String issuer,
        LocalDate acquiredAt,
        LocalDate expiresAt,
        @Size(max = 1000) String officialUrl
) {}
