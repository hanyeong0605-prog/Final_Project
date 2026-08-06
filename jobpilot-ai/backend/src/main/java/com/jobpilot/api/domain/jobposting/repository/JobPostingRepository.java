package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findBySourceProviderAndExternalJobId(String sourceProvider, String externalJobId);
    List<JobPosting> findByStatusOrderByPublishedAtDesc(String status);

    @Query(value = """
            SELECT posting.*
            FROM job_postings posting
            WHERE posting.status = 'ACTIVE'
              AND posting.description IS NOT NULL
              AND CHAR_LENGTH(TRIM(posting.description)) > 0
              AND NOT EXISTS (
                    SELECT 1
                    FROM job_requirements requirement
                    WHERE requirement.job_posting_id = posting.id
              )
              AND NOT EXISTS (
                    SELECT 1
                    FROM job_requirement_extraction_status extraction_status
                    WHERE extraction_status.job_posting_id = posting.id
                      AND extraction_status.status = 'COMPLETED'
              )
            ORDER BY posting.id
            """, nativeQuery = true)
    List<JobPosting> findActiveWithoutRequirementsOrderById();
}
