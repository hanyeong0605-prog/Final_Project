package com.jobpilot.api.domain.jobposting.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores the number of postings attempted on each Korea-local calendar day.
 * Claiming a slot is atomic, so restarts and duplicate schedulers cannot exceed the cap.
 */
@Repository
public class JobRequirementDailyBackfillUsageRepository {
    private final JdbcTemplate jdbcTemplate;

    public JobRequirementDailyBackfillUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claimSlot(LocalDate runDate, int dailyLimit) {
        Date sqlDate = Date.valueOf(runDate);
        int updated = jdbcTemplate.update("""
                UPDATE job_requirement_daily_backfill_usage
                SET processed_count = processed_count + 1
                WHERE run_date = ? AND processed_count < ?
                """, sqlDate, dailyLimit);
        if (updated == 1) return true;

        try {
            jdbcTemplate.update("""
                    INSERT INTO job_requirement_daily_backfill_usage (run_date, processed_count)
                    VALUES (?, 1)
                    """, sqlDate);
            return true;
        } catch (DuplicateKeyException ignored) {
            return jdbcTemplate.update("""
                    UPDATE job_requirement_daily_backfill_usage
                    SET processed_count = processed_count + 1
                    WHERE run_date = ? AND processed_count < ?
                    """, sqlDate, dailyLimit) == 1;
        }
    }

    public int processedCount(LocalDate runDate) {
        List<Integer> counts = jdbcTemplate.query("""
                SELECT processed_count
                FROM job_requirement_daily_backfill_usage
                WHERE run_date = ?
                """, (resultSet, rowNumber) -> resultSet.getInt(1), Date.valueOf(runDate));
        return counts.isEmpty() ? 0 : counts.getFirst();
    }
}
