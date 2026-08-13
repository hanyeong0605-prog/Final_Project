package com.jobpilot.api.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.notification.entity.PushSubscription;
import com.jobpilot.api.domain.notification.repository.PushSubscriptionRepository;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 2026-08-13: web-push(nl.martijndwars) 라이브러리로 VAPID 서명 + Push API 암호화를 처리해서
// 회원의 구독된 기기에 알림을 보낸다. GeminiProjectSummaryClient와 같은 fail-open 패턴 -
// VAPID 키(application.yml의 push.vapid.*)가 비어있으면 그냥 아무것도 안 보내고 조용히
// 넘어간다(로컬 개발 중 키 없이도 다른 기능에 영향 없음).
@Service
public class WebPushService {
    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final PushService pushService;
    private final boolean enabled;

    public WebPushService(
            PushSubscriptionRepository subscriptions,
            ObjectMapper objectMapper,
            @Value("${push.vapid.public-key:}") String publicKey,
            @Value("${push.vapid.private-key:}") String privateKey,
            @Value("${push.vapid.subject:mailto:admin@job-a-dream.site}") String subject
    ) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        // Push API 암호화(ECDH)에 필요한 커브 연산을 표준 JDK 프로바이더가 지원하지 않아
        // BouncyCastle을 JCE 프로바이더로 등록해야 한다 (라이브러리 공식 가이드).
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        PushService created = null;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            try {
                created = new PushService(publicKey, privateKey, subject);
            } catch (GeneralSecurityException e) {
                log.warn("VAPID 키가 올바르지 않아 웹푸시를 비활성화합니다 - {}", e.getMessage());
            }
        }
        this.pushService = created;
        this.enabled = created != null;
    }

    public boolean isEnabled() { return enabled; }

    /** 특정 회원이 구독해둔 모든 기기로 알림을 보낸다. 실패한 구독은 무시하고 계속 진행한다. */
    public void sendToMember(Long memberId, String title, String body, String url) {
        if (!enabled) return;
        List<PushSubscription> targets = subscriptions.findByMemberId(memberId);
        if (targets.isEmpty()) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
        } catch (Exception e) {
            log.warn("웹푸시 payload 직렬화 실패", e);
            return;
        }
        for (PushSubscription subscription : targets) {
            send(subscription, payload);
        }
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuthKey(), payload);
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // 브라우저/OS가 구독을 이미 해지했거나 만료됨 - 다음부턴 조용히 건너뛰게 지운다.
                subscriptions.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 300) {
                log.warn("웹푸시 발송 실패 (status={}) endpoint={}", status, subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("웹푸시 발송 중 오류 - endpoint={}", subscription.getEndpoint(), e);
        }
    }
}
