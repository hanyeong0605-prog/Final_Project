package com.jobpilot.api.domain.jobposting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record JobRequirementExtractionRequest(
        List<@Positive Long> jobPostingIds,
        @Min(1) @Max(20) Integer limit,
        boolean force
) {
}
