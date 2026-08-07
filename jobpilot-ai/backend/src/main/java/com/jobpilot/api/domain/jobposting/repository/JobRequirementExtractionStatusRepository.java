package com.jobpilot.api.domain.jobposting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Records successful GPT Luna attempts, including postings that have no explicit requirements. */
@Repository
public class JobRequirementExtractionStatusRepository {
    private static final String COMPLETED = "COMPLETED";
    private static final String OPENAI_LUNA = "OPENAI_LUNA";

    private final JdbcTemplate jdbcTemplate;

    public JobRequirementExtractionStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isCompleted(Long jobPostingId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM job_requirement_extraction_status
                WHERE job_posting_id = ? AND status = ?
                """, Integer.class, jobPostingId, COMPLETED);
        return count != null && count > 0;
    }

    public void markCompleted(Long jobPostingId) {
        jdbcTemplate.update("""
                INSERT INTO job_requirement_extraction_status
                    (job_posting_id, status, extraction_source, completed_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    extraction_source = VALUES(extraction_source),
                    completed_at = VALUES(completed_at)
                """, jobPostingId, COMPLETED, OPENAI_LUNA);
    }
}
