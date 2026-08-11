package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.dto.JobCrawlRunCompleteRequest;
import com.jobpilot.api.domain.jobposting.dto.JobCrawlRunStartRequest;
import com.jobpilot.api.domain.jobposting.dto.JobCrawlRunStartResponse;
import java.sql.PreparedStatement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class JobCrawlRunService {
    private final JdbcTemplate jdbcTemplate;

    public JobCrawlRunService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JobCrawlRunStartResponse start(JobCrawlRunStartRequest request) {
        String sourceProvider = request.sourceCode() == null || request.sourceCode().isBlank()
                ? "WANTED" : request.sourceCode().trim().toUpperCase();
        String triggerType = request.triggerType() == null || request.triggerType().isBlank()
                ? "MANUAL" : request.triggerType().trim().toUpperCase();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO job_crawl_runs (source_provider, trigger_type, status) VALUES (?, ?, 'RUNNING')",
                    new String[] {"id"});
            statement.setString(1, sourceProvider);
            statement.setString(2, triggerType);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Failed to create crawl run");
        return new JobCrawlRunStartResponse(key.longValue());
    }

    public void complete(Long runId, JobCrawlRunCompleteRequest request) {
        jdbcTemplate.update("""
                        UPDATE job_crawl_runs
                        SET finished_at = NOW(), status = ?, candidate_count = ?, detail_requests = ?,
                            skipped_known_count = ?, collected_count = ?, received_count = ?, created_count = ?,
                            updated_count = ?, skipped_count = ?, failure_count = ?, error_message = ?
                        WHERE id = ?
                        """,
                request.status(), request.candidateCount(), request.detailRequests(), request.skippedKnownCount(),
                request.collectedCount(), request.receivedCount(), request.createdCount(), request.updatedCount(),
                request.skippedCount(), request.failureCount(), request.errorMessage(), runId);
    }
}
