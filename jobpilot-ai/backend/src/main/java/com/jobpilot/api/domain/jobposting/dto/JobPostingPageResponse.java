package com.jobpilot.api.domain.jobposting.dto;

import java.util.List;

public record JobPostingPageResponse(
        List<JobPostingListResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {}
