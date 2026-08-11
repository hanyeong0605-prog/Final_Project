package com.jobpilot.api.domain.jobposting.dto;

public record JobCrawlRunCompleteRequest(
        String status,
        int candidateCount,
        int detailRequests,
        int skippedKnownCount,
        int collectedCount,
        int receivedCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        int failureCount,
        String errorMessage
) {
}
