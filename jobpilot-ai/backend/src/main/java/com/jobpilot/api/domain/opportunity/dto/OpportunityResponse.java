package com.jobpilot.api.domain.opportunity.dto;

import java.util.List;

public record OpportunityResponse(
        Long id,
        String type,
        String title,
        String organization,
        String period,
        String deadline,
        String reason,
        List<String> tags
) {}
