package com.jobpilot.api.domain.notification.repository;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
            Long memberId, String targetType, Long targetId, String notificationType);
}
