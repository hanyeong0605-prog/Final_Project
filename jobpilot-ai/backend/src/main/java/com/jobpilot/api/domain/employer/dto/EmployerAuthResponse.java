package com.jobpilot.api.domain.employer.dto;

public record EmployerAuthResponse(String accessToken, String tokenType, long expiresInSeconds, EmployerResponse employer) {}
