package com.jobpilot.api.domain.notification.controller;

import com.jobpilot.api.domain.notification.dto.NotificationItemResponse;
import com.jobpilot.api.domain.notification.service.NotificationLogService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2026-08-13: 상단바 종 아이콘 - 그동안 눌러도 아무 반응 없던 장식용 버튼이었는데, 이미 있는
// NotificationLog(원래 스케줄러 중복발송 방지용)를 문구까지 저장하도록 확장해서 그대로
// 재사용했다. 새 알림 시스템을 처음부터 만들지 않고 기존 발송 이력 테이블을 넓힌 것.
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationLogService service;

    public NotificationController(NotificationLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationItemResponse> list(Authentication auth) {
        return service.list(AuthenticatedMember.id(auth));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return service.unreadCount(AuthenticatedMember.id(auth));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, Authentication auth) {
        service.markRead(AuthenticatedMember.id(auth), id);
    }

    @PostMapping("/read-all")
    public void markAllRead(Authentication auth) {
        service.markAllRead(AuthenticatedMember.id(auth));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(AuthenticatedMember.id(auth), id);
    }

    @DeleteMapping
    public void deleteAll(Authentication auth) {
        service.deleteAll(AuthenticatedMember.id(auth));
    }
}
