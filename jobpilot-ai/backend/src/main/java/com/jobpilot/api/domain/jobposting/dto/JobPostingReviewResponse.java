package com.jobpilot.api.domain.jobposting.dto;

import java.time.LocalDateTime;

public record JobPostingReviewResponse(Long id, int rating, String content, boolean employmentVerified, boolean mine, LocalDateTime createdAt) {}
