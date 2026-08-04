package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findBySourceProviderAndExternalJobId(String sourceProvider, String externalJobId);
    List<JobPosting> findByStatusOrderByPublishedAtDesc(String status);
}
