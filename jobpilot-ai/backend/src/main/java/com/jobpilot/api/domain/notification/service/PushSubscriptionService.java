package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.notification.dto.PushSubscribeRequest;
import com.jobpilot.api.domain.notification.entity.PushSubscription;
import com.jobpilot.api.domain.notification.repository.PushSubscriptionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PushSubscriptionService {
    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    // endpoint가 UNIQUE라 사실상의 upsert - 같은 브라우저가 재구독했을 때(로그아웃 후 다른
    // 계정으로 재로그인 등) 새 행을 또 만들지 않고 기존 행의 회원/키만 갱신한다.
    public void subscribe(Long memberId, PushSubscribeRequest request, String userAgent) {
        repository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> existing.refresh(memberId, request.p256dh(), request.auth(), userAgent),
                        () -> repository.save(new PushSubscription(
                                memberId, request.endpoint(), request.p256dh(), request.auth(), userAgent)));
    }

    public void unsubscribe(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
