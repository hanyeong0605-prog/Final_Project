package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {
    Optional<JobPosting> findBySourceProviderAndExternalJobId(String sourceProvider, String externalJobId);
    List<JobPosting> findByStatusOrderByPublishedAtDesc(String status);
    long countByStatus(String status);
    Page<JobPosting> findByTitleContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(String title, String companyName, Pageable pageable);

    @Query(value = """
            SELECT * FROM job_postings
            WHERE (:status = 'ALL' OR status = :status)
              AND (
                    :query = ''
                    OR LOWER(COALESCE(title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(company_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY
              CASE WHEN :sort = 'deadline_asc' THEN deadline_at IS NULL ELSE FALSE END ASC,
              CASE WHEN :sort = 'deadline_asc' THEN deadline_at END ASC,
              CASE WHEN :sort = 'deadline_desc' THEN deadline_at IS NULL ELSE FALSE END ASC,
              CASE WHEN :sort = 'deadline_desc' THEN deadline_at END DESC,
              CASE WHEN :sort = 'popular' THEN view_count END DESC,
              CASE WHEN :sort = 'recent' THEN fetched_at END DESC,
              id DESC
            """,
            countQuery = """
                    SELECT COUNT(*) FROM job_postings
                    WHERE (:status = 'ALL' OR status = :status)
                      AND (
                            :query = ''
                            OR LOWER(COALESCE(title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                            OR LOWER(COALESCE(company_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                          )
                    """,
            nativeQuery = true)
    Page<JobPosting> findForAdmin(
            @Param("query") String query,
            @Param("status") String status,
            @Param("sort") String sort,
            Pageable pageable);

    /**
     * Read Wanted's image gallery directly from MySQL JSON.  This avoids relying
     * on Hibernate's JSON conversion for payloads imported from the seed dump.
     */
    @Query(value = """
            SELECT CASE
                WHEN JSON_LENGTH(JSON_EXTRACT(raw_payload, '$.imageUrls')) > 0
                    THEN JSON_EXTRACT(raw_payload, '$.imageUrls')
                ELSE JSON_EXTRACT(raw_payload, '$.images.job_thumbnail_urls')
            END
            FROM job_postings
            WHERE id = :id
            """, nativeQuery = true)
    String findImageUrlsJsonById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE JobPosting posting SET posting.viewCount = posting.viewCount + 1 WHERE posting.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE JobPosting posting SET posting.status = 'CLOSED' "
            + "WHERE posting.status = 'ACTIVE' AND posting.deadlineAt IS NOT NULL AND posting.deadlineAt < :now")
    int closeExpiredActive(@Param("now") java.time.LocalDateTime now);

    @Query(value = """
            SELECT posting.* FROM job_postings posting
            WHERE posting.status = 'ACTIVE'
              AND (posting.deadline_at IS NULL OR posting.deadline_at >= NOW())
              AND EXISTS (SELECT 1 FROM job_requirements requirement WHERE requirement.job_posting_id = posting.id)
            ORDER BY posting.deadline_at IS NULL, posting.deadline_at ASC, posting.published_at DESC
            """, nativeQuery = true)
    List<JobPosting> findActiveWithRequirements();

    @Query(value = """
            SELECT posting.*
            FROM job_postings posting
            WHERE posting.status = 'ACTIVE'
              AND (posting.deadline_at IS NULL OR posting.deadline_at >= NOW())
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
