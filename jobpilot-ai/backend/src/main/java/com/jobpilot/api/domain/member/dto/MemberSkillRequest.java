package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemberSkillRequest(
        @NotNull Long skillId,
        @Size(max = 30) String selfReportedLevel,
        @Size(max = 300) String note
) {}
