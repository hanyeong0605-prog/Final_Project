package com.jobpilot.api.domain.matching.repository;

import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    List<JobMatch> findByMemberIdOrderByReadinessScoreDesc(Long memberId);
    List<JobMatch> findByMemberIdAndRecommendationLevelOrderByReadinessScoreDesc(Long memberId, RecommendationLevel level);
    Optional<JobMatch> findByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);
    void deleteByMemberId(Long memberId);
    void deleteByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);

    // 2026-08-13: RecommendedJobPushScheduler가 "최근에 새로 APPLY_NOW로 매칭된 공고"만 골라
    // 알림을 보내는 데 쓴다 - JobRequirementExtractionService.regenerateForPosting(...)가
    // 새 공고를 분석할 때마다 이 analyzedAt이 갱신된다.
    List<JobMatch> findByRecommendationLevelAndAnalyzedAtAfterOrderByReadinessScoreDesc(
            RecommendationLevel recommendationLevel, LocalDateTime analyzedAtAfter);
}
