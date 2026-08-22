package com.jobpilot.api.domain.matching.dto;

/** A deterministic, requirement-keyword based learning resource link. */
public record GrowthResourceRecommendationResponse(
        String type, String label, String title, String description, String href
) {}
