package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.entity.SubscriptionStatus;
import com.jobpilot.api.domain.subscription.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 2026-08-29: 구독 만료 예고/종료 알림.
//
// 왜 필요한가 - 토스페이먼츠 자동결제(빌링)는 테스트 환경에서도 별도 계약이 필요해서 쓰지
// 못했고(SubscriptionSection.tsx의 2026-08-10 메모 참고), 그래서 결제 한 건이 딱 1개월치만
// 준다. SubscriptionService.expireOverdueSubscriptions가 매일 새벽 3시에 기간이 지난 구독을
// 그냥 해지해버리는데, 지금까지 사용자에게는 아무 안내도 가지 않았다 - 어느 날 갑자기
// 실전면접이 잠기고 이유를 알 수 없는 상태가 된다. 자동으로 재결제해줄 수 없으니 최소한
// "곧 끝난다 / 끝났다, 계속 쓰려면 재신청해라"는 알림은 보내야 한다.
//
// DeadlineReminderScheduler(찜한 공고 마감임박)와 같은 패턴이다 - 다만 한 가지가 다르다:
// 그쪽은 VAPID 키가 없으면 스캔 자체를 건너뛰어서 알림 이력(NotificationLog)도 안 남는데,
// 여기서는 이력을 항상 남기고 웹푸시 발송만 건너뛴다. 이력이 남아야 앱 안 알림함(종 아이콘,
// NotificationController)에는 보이기 때문이다 - 푸시를 못 켠 사용자(iOS에서 홈 화면에
// 추가하지 않은 경우 등)에게도 구독 만료만큼은 알려져야 한다.
@Component
public class SubscriptionExpiryReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryReminderScheduler.class);

    private static final String TARGET_TYPE = "SUBSCRIPTION";
    /** 만료 며칠 전에 미리 알릴지. 너무 이르면 "아직 여유 있네" 하고 잊혀서 짧게 잡았다. */
    private static final List<Integer> REMINDER_DAYS_BEFORE = List.of(3, 1);
    /** 알림 종류에 붙일 결제 주기 식별자 - 아래 notificationType 설계 메모 참고. */
    private static final DateTimeFormatter PERIOD_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SUBSCRIPTION_URL = "/account";

    private final SubscriptionRepository subscriptions;
    private final NotificationLogRepository notificationLogs;
    private final WebPushService webPush;

    public SubscriptionExpiryReminderScheduler(
            SubscriptionRepository subscriptions,
            NotificationLogRepository notificationLogs,
            WebPushService webPush
    ) {
        this.subscriptions = subscriptions;
        this.notificationLogs = notificationLogs;
        this.webPush = webPush;
    }

    /**
     * 매일 오전 9시 30분(KST). 마감임박 알림(09:00) 바로 뒤에 둬서 알림이 한 번에 몰리되
     * 순서는 공고 → 구독으로 고정되게 했다. 자동 해지 스케줄러(새벽 3시)보다 늦게 돌기 때문에
     * "오늘 새벽에 만료된 구독"도 같은 실행에서 함께 안내할 수 있다.
     */
    @Scheduled(cron = "${subscription.expiry-reminder.cron:0 30 9 * * *}", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    public void sendSubscriptionExpiryNotifications() {
        int sent = notifyUpcomingExpiry() + notifyJustExpired();
        if (sent > 0) log.info("구독 만료 관련 알림 {}건 발송", sent);
    }

    /** 아직 구독 중이지만 만료가 코앞인 회원에게 미리 알린다. */
    private int notifyUpcomingExpiry() {
        LocalDate today = LocalDate.now();
        int sent = 0;
        for (Subscription subscription : subscriptions.findByStatus(SubscriptionStatus.ACTIVE)) {
            if (subscription.getCurrentPeriodEnd() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, subscription.getCurrentPeriodEnd().toLocalDate());
            if (!REMINDER_DAYS_BEFORE.contains((int) daysLeft)) continue;

            boolean delivered = notifyOnce(
                    subscription,
                    "SUBSCRIPTION_EXPIRY_D" + daysLeft,
                    "구독이 " + daysLeft + "일 뒤 끝나요",
                    "자동 결제가 아니라서 그대로 두면 종료돼요. 계속 이용하시려면 재신청해 주세요.");
            if (delivered) sent++;
        }
        return sent;
    }

    /**
     * 오늘 새벽 자동 해지된 구독에 "끝났으니 재신청하라"고 알린다.
     *
     * 사용자가 직접 해지한 경우는 제외해야 한다 - 본인이 방금 누르고 나왔는데 "끝났어요,
     * 재신청하세요"가 오면 붙잡는 광고로 읽힌다. 직접 해지는 기간이 남아 있는 시점에
     * 이뤄지므로(canceledAt < currentPeriodEnd), 그 반대인 경우만 자동 만료로 본다.
     */
    private int notifyJustExpired() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        int sent = 0;
        for (Subscription subscription : subscriptions.findByStatusAndCanceledAtGreaterThanEqual(
                SubscriptionStatus.CANCELED, since)) {
            LocalDateTime canceledAt = subscription.getCanceledAt();
            LocalDateTime periodEnd = subscription.getCurrentPeriodEnd();
            if (canceledAt == null || periodEnd == null || canceledAt.isBefore(periodEnd)) continue;

            boolean delivered = notifyOnce(
                    subscription,
                    "SUBSCRIPTION_EXPIRED",
                    "구독이 종료됐어요",
                    "실전면접 등 구독 기능을 계속 이용하시려면 재신청해 주세요.");
            if (delivered) sent++;
        }
        return sent;
    }

    /**
     * 같은 결제 주기에 같은 알림을 두 번 보내지 않는다.
     *
     * notificationType에 만료일(yyyyMMdd)을 붙이는 게 핵심이다. 구독 레코드는 회원당 하나라
     * (member_id UNIQUE) 재신청해도 id가 그대로인데, 종류가 그냥 "SUBSCRIPTION_EXPIRY_D3"면
     * 다음 달 만료 예고가 지난달 이력에 걸려 영영 안 나간다.
     */
    private boolean notifyOnce(Subscription subscription, String kind, String title, String body) {
        String notificationType = kind + "_" + subscription.getCurrentPeriodEnd().toLocalDate().format(PERIOD_KEY);
        boolean alreadySent = notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                subscription.getMemberId(), TARGET_TYPE, subscription.getId(), notificationType);
        if (alreadySent) return false;

        // 이력은 항상 남기고(앱 안 알림함용), 웹푸시는 켜져 있을 때만 나간다.
        // WebPushService.sendToMember 자체가 VAPID 키 없으면 조용히 no-op이라 분기가 필요 없다.
        webPush.sendToMember(subscription.getMemberId(), title, body, SUBSCRIPTION_URL);
        notificationLogs.save(new NotificationLog(
                subscription.getMemberId(), TARGET_TYPE, subscription.getId(), notificationType,
                title, body, SUBSCRIPTION_URL));
        return true;
    }
}
