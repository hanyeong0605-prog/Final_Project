package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {}
