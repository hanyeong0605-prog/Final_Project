package com.jobpilot.api.domain.member.dto;

public record MemberSkillResponse(
        Long skillId,
        String skillName,
        String category,
        String selfReportedLevel,
        String note
) {}
