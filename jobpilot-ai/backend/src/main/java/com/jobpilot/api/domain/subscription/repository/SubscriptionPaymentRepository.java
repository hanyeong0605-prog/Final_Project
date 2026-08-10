package com.jobpilot.api.domain.subscription.repository;

import com.jobpilot.api.domain.subscription.entity.SubscriptionPayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<SubscriptionPayment> findByOrderIdAndMemberId(String orderId, Long memberId);
}
