package com.jobpilot.api.domain.review.dto;

import jakarta.validation.constraints.*;

/** Fields are bounded individually; the domain also bounds the combined inference text to 5000. */
public record ReviewRequest(
        @Positive Long jobPostingId,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1500) String pros,
        @NotBlank @Size(max = 1500) String cons,
        @NotBlank @Size(max = 5000) String body) {}
