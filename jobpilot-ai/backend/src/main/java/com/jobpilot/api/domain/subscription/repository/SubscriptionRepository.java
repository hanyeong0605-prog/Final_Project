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

    // 2026-08-29: 만료 예고 알림용 - 아직 살아있는 구독 전체를 훑는다. 구독자 수가 많아지면
    // currentPeriodEnd 범위 조건을 넣어 좁혀야 하지만, 지금 규모에서는 전체 스캔이 더 단순하다.
    List<Subscription> findByStatus(SubscriptionStatus status);

    // 자동 만료로 방금 해지된 구독을 찾을 때 쓴다(사용자가 직접 해지한 것과의 구분은 호출부에서 한다).
    List<Subscription> findByStatusAndCanceledAtGreaterThanEqual(SubscriptionStatus status, LocalDateTime since);
}
