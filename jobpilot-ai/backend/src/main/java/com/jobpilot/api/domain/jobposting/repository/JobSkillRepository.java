package com.jobpilot.api.domain.jobposting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JobSkillRepository {
    private final JdbcTemplate jdbcTemplate;

    public JobSkillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteByJobPostingId(Long jobPostingId) {
        jdbcTemplate.update("DELETE FROM job_skills WHERE job_posting_id = ?", jobPostingId);
    }

    public void save(Long jobPostingId, Long skillId, String requirementType, String sourceExcerpt) {
        jdbcTemplate.update("""
                INSERT INTO job_skills (job_posting_id, skill_id, requirement_type, source_excerpt)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE source_excerpt = VALUES(source_excerpt)
                """, jobPostingId, skillId, requirementType, sourceExcerpt);
    }
}
