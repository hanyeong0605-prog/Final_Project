package com.jobpilot.api.domain.subscription.repository;

import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.entity.SubscriptionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByMemberId(Long memberId);

    // 매일 스케줄러가 "오늘까지 다음 결제일이 도래한 활성 구독"을 찾을 때 쓴다.
    List<Subscription> findByStatusAndNextBillingAtLessThanEqual(SubscriptionStatus status, LocalDateTime now);
}
