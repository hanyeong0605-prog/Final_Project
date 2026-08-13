package com.jobpilot.api.domain.interest.repository;

import com.jobpilot.api.domain.interest.entity.UserInterest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
    List<UserInterest> findByMemberIdAndTargetTypeOrderByCreatedAtDesc(Long memberId, String targetType);
    Optional<UserInterest> findByMemberIdAndTargetTypeAndTargetId(Long memberId, String targetType, Long targetId);
    // 2026-08-13: DeadlineReminderScheduler가 "찜한 공고" 전체(모든 회원)를 순회하기 위해 추가.
    List<UserInterest> findByTargetType(String targetType);
}
