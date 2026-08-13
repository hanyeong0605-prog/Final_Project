package com.jobpilot.api.domain.notification.repository;

import com.jobpilot.api.domain.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);
    List<PushSubscription> findByMemberId(Long memberId);
    void deleteByEndpoint(String endpoint);

    // 2026-08-13: RecommendedJobPushScheduler가 "푸시를 받을 수 있는 회원"만 골라내는 데 쓴다 -
    // 전체 회원을 순회하며 매칭 계산을 하는 대신, 구독이 하나라도 있는 회원으로 먼저 좁힌다.
    @Query("select distinct s.memberId from PushSubscription s")
    List<Long> findDistinctMemberIds();
}
