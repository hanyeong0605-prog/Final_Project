package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {
    boolean existsByJobPostingId(Long jobPostingId);
    void deleteByJobPostingId(Long jobPostingId);
    List<JobRequirement> findByJobPostingId(Long jobPostingId);
}
