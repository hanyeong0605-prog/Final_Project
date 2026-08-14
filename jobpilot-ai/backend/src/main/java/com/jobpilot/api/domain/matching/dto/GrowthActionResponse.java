package com.jobpilot.api.domain.matching.dto;
public record GrowthActionResponse(Long requirementId, String requirement, String category, String title,
        String description, String nextStep, String href) {}
