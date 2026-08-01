package com.jobpilot.api.domain.matching.repository;

import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    List<JobMatch> findByMemberIdOrderByReadinessScoreDesc(Long memberId);
    List<JobMatch> findByMemberIdAndRecommendationLevelOrderByReadinessScoreDesc(Long memberId, RecommendationLevel level);
    Optional<JobMatch> findByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);
}
