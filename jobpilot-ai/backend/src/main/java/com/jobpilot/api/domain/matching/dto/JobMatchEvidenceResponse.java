package com.jobpilot.api.domain.matching.dto;

public record JobMatchEvidenceResponse(
        Long requirementId,
        String requirement,
        String requirementType,
        String sourceExcerpt,
        String memberEvidenceType,
        Long memberEvidenceId,
        String status,
        String comment,
        String gapAction
) {}
