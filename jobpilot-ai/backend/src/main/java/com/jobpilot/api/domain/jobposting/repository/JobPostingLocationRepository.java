package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPostingLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingLocationRepository extends JpaRepository<JobPostingLocation, Long> {
    List<JobPostingLocation> findByJobPostingIdOrderByPrimaryLocationDescIdAsc(Long jobPostingId);
}
