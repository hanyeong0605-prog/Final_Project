package com.jobpilot.api.domain.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        MemberResponse member
) {}
