package com.jobpilot.api.domain.matching.repository;

import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.policy.MatchGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    List<JobMatch> findByMemberIdOrderByScoreDesc(Long memberId);
    List<JobMatch> findByMemberIdAndGradeOrderByScoreDesc(Long memberId, MatchGrade grade);
    Optional<JobMatch> findByMemberIdAndJobPostingId(Long memberId, Long jobPostingId);
}
