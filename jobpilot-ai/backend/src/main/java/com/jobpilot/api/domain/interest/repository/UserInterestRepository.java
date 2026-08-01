package com.jobpilot.api.domain.interest.repository;

import com.jobpilot.api.domain.interest.entity.UserInterest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {
    List<UserInterest> findByMemberIdAndTargetTypeOrderByCreatedAtDesc(Long memberId, String targetType);
    Optional<UserInterest> findByMemberIdAndTargetTypeAndTargetId(Long memberId, String targetType, Long targetId);
}
