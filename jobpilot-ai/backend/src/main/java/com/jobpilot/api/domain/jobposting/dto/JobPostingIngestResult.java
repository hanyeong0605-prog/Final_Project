package com.jobpilot.api.domain.jobposting.dto;

public record JobPostingIngestResult(
        int received,
        int created,
        int updated,
        int skipped
) {
}
