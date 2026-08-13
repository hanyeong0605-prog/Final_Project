package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.notification.dto.NotificationItemResponse;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// 2026-08-13: 상단바 종 아이콘 드롭다운용 읽기/읽음처리 서비스. 엔티티 필드를 바꾸는
// markRead()/markAllReadByMemberId(@Modifying)는 트랜잭션 안에서 호출돼야 해서 컨트롤러에
// 바로 두지 않고 이 서비스로 분리했다(PushSubscriptionService와 같은 패턴).
@Service
@Transactional
public class NotificationLogService {
    private final NotificationLogRepository notificationLogs;

    public NotificationLogService(NotificationLogRepository notificationLogs) {
        this.notificationLogs = notificationLogs;
    }

    public List<NotificationItemResponse> list(Long memberId) {
        return notificationLogs.findTop30ByMemberIdOrderBySentAtDesc(memberId).stream()
                .map(NotificationItemResponse::from)
                .toList();
    }

    public Map<String, Long> unreadCount(Long memberId) {
        return Map.of("count", notificationLogs.countByMemberIdAndReadFalse(memberId));
    }

    public void markRead(Long memberId, Long id) {
        NotificationLog log = notificationLogs.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
        log.markRead();
    }

    public void markAllRead(Long memberId) {
        notificationLogs.markAllReadByMemberId(memberId);
    }
}
