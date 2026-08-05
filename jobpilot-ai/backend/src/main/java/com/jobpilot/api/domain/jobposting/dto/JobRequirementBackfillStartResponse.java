package com.jobpilot.api.domain.jobposting.dto;

public record JobRequirementBackfillStartResponse(
        boolean started,
        int totalCandidates,
        int batchSize,
        String message
) {
}
