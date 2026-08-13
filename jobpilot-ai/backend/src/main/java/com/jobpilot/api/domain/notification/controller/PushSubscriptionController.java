package com.jobpilot.api.domain.notification.controller;

import com.jobpilot.api.domain.notification.dto.PushSubscribeRequest;
import com.jobpilot.api.domain.notification.service.PushSubscriptionService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {
    private final PushSubscriptionService service;
    private final String vapidPublicKey;

    public PushSubscriptionController(
            PushSubscriptionService service,
            @Value("${push.vapid.public-key:}") String vapidPublicKey
    ) {
        this.service = service;
        this.vapidPublicKey = vapidPublicKey;
    }

    // 프론트가 PushManager.subscribe({ applicationServerKey })에 넘길 공개키.
    // 키가 설정 안 된 환경(로컬 개발 등)이면 빈 문자열을 돌려주고, 프론트는 이 경우 알림
    // 켜기 UI 자체를 숨긴다(PushNotificationSection 참고) - fail-open.
    @GetMapping("/vapid-public-key")
    public Map<String, String> vapidPublicKey() {
        return Map.of("publicKey", vapidPublicKey);
    }

    @PostMapping("/subscribe")
    public void subscribe(
            @Valid @RequestBody PushSubscribeRequest request,
            Authentication auth,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        service.subscribe(AuthenticatedMember.id(auth), request, userAgent);
    }

    @PostMapping("/unsubscribe")
    public void unsubscribe(@RequestBody Map<String, String> body) {
        service.unsubscribe(body.get("endpoint"));
    }
}
