package com.jobpilot.api.domain.employer.controller;

import com.jobpilot.api.domain.employer.repository.EmployerNotificationRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employer/notifications")
@Transactional
public class EmployerNotificationController {
    private final EmployerNotificationRepository notifications;
    public EmployerNotificationController(EmployerNotificationRepository notifications) { this.notifications = notifications; }
    @GetMapping public List<Item> list(Authentication auth) { return notifications.findTop30ByEmployerAccountIdOrderByCreatedAtDesc(AuthenticatedEmployer.id(auth)).stream().map(Item::from).toList(); }
    @GetMapping("/unread-count") public Map<String, Long> unread(Authentication auth) { return Map.of("count", notifications.countByEmployerAccountIdAndReadFalse(AuthenticatedEmployer.id(auth))); }
    @PostMapping("/{id}/read") public void read(@PathVariable Long id, Authentication auth) { notifications.findByIdAndEmployerAccountId(id, AuthenticatedEmployer.id(auth)).orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다.")).markRead(); }
    public record Item(Long id, String title, String body, String url, boolean read, LocalDateTime sentAt) {
        static Item from(com.jobpilot.api.domain.employer.entity.EmployerNotification value) { return new Item(value.getId(), value.getTitle(), value.getBody(), value.getUrl(), value.isRead(), value.getCreatedAt()); }
    }
}
