package com.jobpilot.api.domain.matching.dto;

import java.util.List;

public record GrowthActionResponse(Long requirementId, String requirement, String category, String title,
        String description, String nextStep, String href, List<Long> relatedRequirementIds,
        List<GrowthResourceRecommendationResponse> recommendations) {}
