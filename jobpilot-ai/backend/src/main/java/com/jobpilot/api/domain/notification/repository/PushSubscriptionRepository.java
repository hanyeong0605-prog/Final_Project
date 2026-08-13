package com.jobpilot.api.domain.notification.repository;

import com.jobpilot.api.domain.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    List<PushSubscription> findByMemberId(Long memberId);
    void deleteByEndpoint(String endpoint);
}
