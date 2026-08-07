package com.jobpilot.api.domain.jobposting.dto;

public record JobRequirementBackfillStatusResponse(
        boolean running,
        int totalCandidates,
        int processed,
        int extracted,
        int skipped,
        int failed,
        int remaining,
        String state,
        String message
) {
}
