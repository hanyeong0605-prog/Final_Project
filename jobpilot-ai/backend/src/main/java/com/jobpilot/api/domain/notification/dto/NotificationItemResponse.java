package com.jobpilot.api.domain.notification.dto;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import java.time.LocalDateTime;

// 상단바 종 아이콘 드롭다운에 뿌릴 알림 1건. title/body/url은 발송 당시 스냅샷
// (NotificationLog 참고) - 대상 공고가 이후 삭제/변경돼도 알림 문구는 그대로 남는다.
public record NotificationItemResponse(
        Long id,
        String title,
        String body,
        String url,
        boolean read,
        LocalDateTime sentAt
) {
    public static NotificationItemResponse from(NotificationLog log) {
        return new NotificationItemResponse(
                log.getId(), log.getTitle(), log.getBody(), log.getUrl(), log.isRead(), log.getSentAt());
    }
}
