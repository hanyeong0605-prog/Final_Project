package com.jobpilot.api.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// "이 회원에게 이 대상으로 이 종류의 알림을 이미 보냈는지"만 기록하는 최소 이력 테이블 -
// DeadlineReminderScheduler가 매일 돌 때 같은 공고에 대해 같은 알림을 중복 발송하지 않기
// 위한 용도. V22__push_notifications.sql 주석 참고.
@Entity
@Table(name = "notification_logs")
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "target_type", nullable = false) private String targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "notification_type", nullable = false) private String notificationType;
    @Column(name = "sent_at", nullable = false) private LocalDateTime sentAt;

    protected NotificationLog() {}

    public NotificationLog(Long memberId, String targetType, Long targetId, String notificationType) {
        this.memberId = memberId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.notificationType = notificationType;
        this.sentAt = LocalDateTime.now();
    }
}
