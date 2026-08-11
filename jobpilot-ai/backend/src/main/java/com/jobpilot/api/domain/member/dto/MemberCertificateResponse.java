package com.jobpilot.api.domain.member.dto;

import java.time.LocalDate;

public record MemberCertificateResponse(
        Long id,
        String name,
        String issuer,
        LocalDate acquiredAt,
        LocalDate expiresAt,
        String officialUrl
) {}
