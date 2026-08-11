package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SelfIntroductionRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 8000) String content,
        boolean primary
) {}
