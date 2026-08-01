package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(@NotBlank @Size(min = 2, max = 80) String nickname) {}
