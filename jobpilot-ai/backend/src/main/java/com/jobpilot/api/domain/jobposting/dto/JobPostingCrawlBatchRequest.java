package com.jobpilot.api.domain.jobposting.dto;

import java.util.List;

public record JobPostingCrawlBatchRequest(
        String sourceCode,
        List<JobPostingCrawlItem> items
) {
}
