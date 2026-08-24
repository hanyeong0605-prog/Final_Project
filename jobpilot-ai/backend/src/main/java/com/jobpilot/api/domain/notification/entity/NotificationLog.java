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
//
// 2026-08-13: 상단바 종 아이콘 알림 드롭다운 추가하면서 title/body/url/read를 더했다 -
// 원래 중복방지 전용이었지만, 발송 당시 WebPushService에 넘긴 문구를 그대로 스냅샷해두면
// 화면에 뿌릴 알림 이력으로도 재사용할 수 있어서 별도 테이블을 새로 만들지 않았다.
@Entity
@Table(name = "notification_logs")
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "target_type", nullable = false) private String targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "notification_type", nullable = false) private String notificationType;
    @Column(name = "title") private String title;
    @Column(name = "body") private String body;
    @Column(name = "url") private String url;
    @Column(name = "is_read", nullable = false) private boolean read;
    @Column(name = "is_hidden", nullable = false) private boolean hidden;
    @Column(name = "sent_at", nullable = false) private LocalDateTime sentAt;

    protected NotificationLog() {}

    /** 문구 없이 중복방지 목적으로만 기록하던 기존 호출부 호환용. */
    public NotificationLog(Long memberId, String targetType, Long targetId, String notificationType) {
        this(memberId, targetType, targetId, notificationType, null, null, null);
    }

    public NotificationLog(
            Long memberId, String targetType, Long targetId, String notificationType,
            String title, String body, String url
    ) {
        this.memberId = memberId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.notificationType = notificationType;
        this.title = title;
        this.body = body;
        this.url = url;
        this.read = false;
        this.hidden = false;
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getUrl() { return url; }
    public boolean isRead() { return read; }
    public LocalDateTime getSentAt() { return sentAt; }

    public void markRead() { this.read = true; }
    public void hide() { this.hidden = true; }
}
