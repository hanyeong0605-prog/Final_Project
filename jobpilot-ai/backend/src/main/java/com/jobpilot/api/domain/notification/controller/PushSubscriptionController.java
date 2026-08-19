package com.jobpilot.api.domain.notification.controller;

import com.jobpilot.api.domain.admin.AdminAccessService;
import com.jobpilot.api.domain.notification.dto.PushSubscribeRequest;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.notification.service.PushSubscriptionService;
import com.jobpilot.api.domain.notification.service.WebPushService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {
    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionController.class);

    private final PushSubscriptionService service;
    private final WebPushService webPush;
    private final AdminAccessService adminAccess;
    private final NotificationLogRepository notificationLogs;
    private final String vapidPublicKey;

    public PushSubscriptionController(
            PushSubscriptionService service,
            WebPushService webPush,
            AdminAccessService adminAccess,
            NotificationLogRepository notificationLogs,
            @Value("${push.vapid.public-key:}") String vapidPublicKey
    ) {
        this.service = service;
        this.webPush = webPush;
        this.adminAccess = adminAccess;
        this.notificationLogs = notificationLogs;
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

    // 2026-08-13: 관리자 전용 테스트 발송 - 마감임박/추천 스케줄러는 하루 한 번(08:30/09:00)만
    // 돌고 조건(마감 D-3·D-1, 새 APPLY_NOW 매칭)도 맞아야 해서 실기기(특히 iOS - 홈 화면
    // 추가 후 구독이 되는지) 테스트가 번거롭다. 본인 계정으로 즉시 테스트 알림 1건을
    // 쏴보는 용도 - 다른 회원에게는 못 보낸다(항상 호출한 관리자 자신에게만 발송).
    @PostMapping("/test-send")
    public Map<String, Object> testSend(Authentication auth) {
        Long memberId = AuthenticatedMember.id(auth);
        adminAccess.requireAdmin(memberId);
        if (!webPush.isEnabled()) {
            return Map.of("sent", false, "reason", "VAPID 키가 설정되지 않아 웹푸시가 비활성화되어 있습니다.");
        }
        String title = "테스트 알림";
        String body = "웹푸시가 정상적으로 도착했어요.";
        String url = "/mypage";
        webPush.sendToMember(memberId, title, body, url);
        // 2026-08-19: 버그 수정 - notification_logs에는 (member_id, target_type, target_id,
        // notification_type) 유니크 제약(V25)이 걸려 있는데, 테스트 발송은 항상 같은 조합
        // (memberId, "TEST", memberId, "TEST")으로 저장하려고 해서 관리자가 "테스트 알림
        // 보기"를 두 번째 누르는 순간부터 DB가 중복 키를 거부해 DataIntegrityViolationException이
        // 그대로 500으로 튀어나왔다(마감임박/추천 스케줄러는 저장 전에 existsBy...로 먼저
        // 확인하는데, 이 테스트 엔드포인트만 그 가드가 없었음). 실제 웹푸시 발송(위 sendToMember)은
        // 이미 끝난 뒤라 로그 저장 실패 때문에 "발송 자체가 실패했다"고 보여줄 이유가 없어서,
        // 로그 저장은 fail-open으로 처리한다(저장 실패해도 응답은 sent:true 그대로).
        try {
            // target_id는 NOT NULL이라 실제 대상이 없는 테스트 발송은 memberId 자신을 넣어둔다.
            notificationLogs.save(new NotificationLog(memberId, "TEST", memberId, "TEST", title, body, url));
        } catch (DataIntegrityViolationException e) {
            log.info("테스트 알림 로그 저장 생략(이미 같은 조합의 로그가 있음, memberId={})", memberId);
        }
        return Map.of("sent", true);
    }
}
