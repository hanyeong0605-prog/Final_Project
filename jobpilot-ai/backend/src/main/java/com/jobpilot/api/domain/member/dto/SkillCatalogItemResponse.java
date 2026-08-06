package com.jobpilot.api.domain.member.dto;

public record SkillCatalogItemResponse(
        Long id,
        String name,
        String category,
        Long parentSkillId
) {}
