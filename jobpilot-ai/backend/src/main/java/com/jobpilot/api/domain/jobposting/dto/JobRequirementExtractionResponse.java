package com.jobpilot.api.domain.jobposting.dto;

import java.util.List;

public record JobRequirementExtractionResponse(
        int selected,
        int extracted,
        int skipped,
        int failed,
        List<Item> items
) {
    public record Item(
            Long jobPostingId,
            String title,
            String status,
            int requirementCount,
            String message
    ) {
    }
}
