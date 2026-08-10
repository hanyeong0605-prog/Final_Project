package com.jobpilot.api.domain.member.dto;

import java.time.LocalDateTime;

public record SelfIntroductionResponse(
        Long id,
        String title,
        String content,
        boolean primary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
