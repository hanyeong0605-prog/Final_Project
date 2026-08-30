package com.jobpilot.api.domain.jobposting.repository;

import com.jobpilot.api.domain.jobposting.entity.JobPostingReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingReviewRepository extends JpaRepository<JobPostingReview, Long> {
    List<JobPostingReview> findByJobPostingIdOrderByCreatedAtDesc(Long jobPostingId);
    Optional<JobPostingReview> findByJobPostingIdAndMemberId(Long jobPostingId, Long memberId);
}
