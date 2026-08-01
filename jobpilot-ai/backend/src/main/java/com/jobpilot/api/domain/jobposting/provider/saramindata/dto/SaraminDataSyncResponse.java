package com.jobpilot.api.domain.jobposting.provider.saramindata.dto;

public record SaraminDataSyncResponse(
        String provider, int fetched, int created, int updated, int skipped, int failed
) {}
