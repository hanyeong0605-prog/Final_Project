package com.jobpilot.api.domain.matching.dto;

import java.util.List;

public record JobMatchDetailResponse(
        JobMatchSummaryResponse match,
        List<JobMatchEvidenceResponse> evidences,
        String postingDescription
) {}
