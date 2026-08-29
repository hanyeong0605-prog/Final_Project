package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.repository.SubscriptionRepository;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 2026-08-29: 이용권 잔여 횟수 알림.
//
// 왜 필요한가 - 잔여 횟수가 0이 되는 순간은 사용자가 실전면접을 막 시작한 참이라, 화면에
// 떠 있어도 신경 쓰지 않는다. 정작 아쉬운 건 다음에 들어와서 실전면접을 누를 때인데, 그때는
// 이미 하려던 걸 못 하는 상황이다. 그 전에 알려줄 통로가 필요했다.
//
// 다만 "0회입니다, 구매하세요"를 주기적으로 보내면 그건 안내가 아니라 광고다. 그래서 알림의
// 명분을 뒤집었다 - 매달 지급되는 무료 1회를 알리면서 그 김에 현재 잔여 횟수를 같이 알린다.
// 사용자에게 이득이 되는 소식이고, 한 달에 한 번뿐이며, "매달 무료 1회"라는 정책 자체를
// 인지시키는 효과도 있다(안 그러면 아무도 모른 채 지나간다).
//
// 지급 자체는 원래 조회 시점에 lazy로 이뤄지는데(SubscriptionService.grantMonthlyFree),
// 그러면 사이트에 들어온 사람만 받는다. 이 배치가 매달 1일에 먼저 돌아 지급 + 알림을 하고,
// lazy 지급은 그 이후 신규 가입자·미방문자를 위한 안전망으로 남는다.
// grantMonthlyFreeIfEligible이 "그 달에 이미 줬으면 false"라 이중 지급은 일어나지 않는다.
@Component
public class InterviewPassNoticeScheduler {
    private static final Logger log = LoggerFactory.getLogger(InterviewPassNoticeScheduler.class);

    private static final String TARGET_TYPE = "SUBSCRIPTION";
    private static final String MOCK_INTERVIEW_URL = "/mock-interview";

    private final SubscriptionRepository subscriptions;
    private final NotificationLogRepository notificationLogs;
    private final WebPushService webPush;

    public InterviewPassNoticeScheduler(
            SubscriptionRepository subscriptions,
            NotificationLogRepository notificationLogs,
            WebPushService webPush
    ) {
        this.subscriptions = subscriptions;
        this.notificationLogs = notificationLogs;
        this.webPush = webPush;
    }

    /**
     * 매달 1일 오전 9시 10분(KST) - 무료 1회를 지급하고, 지급받은 사람에게만 알린다.
     *
     * 마감임박 알림(09:00) 바로 뒤에 둬서 알림이 한 번에 몰리되 순서는 공고 → 이용권으로
     * 고정된다. 이용권 행이 있는 회원만 훑는다 - 행이 없다는 건 모의면접 화면에 한 번도
     * 들어온 적이 없다는 뜻이라, 그 사람에게는 첫 방문 때 lazy로 지급된다.
     */
    @Scheduled(cron = "${subscription.free-pass-notice.cron:0 10 9 1 * *}", zone = "Asia/Seoul")
    @Transactional
    public void grantAndAnnounceMonthlyFreePass() {
        String month = YearMonth.now().toString();
        int sent = 0;

        // 지금 규모에서는 전체 스캔이 가장 단순하다. 회원이 크게 늘면 free_granted_month가
        // 이번 달이 아닌 행만 골라오는 조회로 좁혀야 한다.
        for (Subscription subscription : subscriptions.findAll()) {
            if (!subscription.grantMonthlyFreeIfEligible(month)) continue;

            // 같은 달에 두 번 알리지 않는다. 지급 자체가 이미 멱등이지만, 배치가 재실행되는
            // 경우(수동 트리거 등)를 대비해 알림 쪽에도 같은 잠금을 건다.
            String notificationType = "FREE_PASS_" + month;
            boolean alreadySent = notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                    subscription.getMemberId(), TARGET_TYPE, subscription.getId(), notificationType);
            if (alreadySent) continue;

            String title = "이번 달 무료 이용권이 도착했어요";
            String body = "지금 실전면접을 " + subscription.getRemainingSessions() + "회 이용할 수 있어요.";

            // 이력은 항상 남기고 웹푸시만 조건부로 나간다(WebPushService가 키 없으면 스스로
            // no-op) - 푸시를 못 켠 사용자도 앱 안 알림함에서는 볼 수 있어야 한다.
            webPush.sendToMember(subscription.getMemberId(), title, body, MOCK_INTERVIEW_URL);
            notificationLogs.save(new NotificationLog(
                    subscription.getMemberId(), TARGET_TYPE, subscription.getId(), notificationType,
                    title, body, MOCK_INTERVIEW_URL));
            sent++;
        }

        if (sent > 0) log.info("무료 이용권 지급 알림 {}건 발송", sent);
    }
}
