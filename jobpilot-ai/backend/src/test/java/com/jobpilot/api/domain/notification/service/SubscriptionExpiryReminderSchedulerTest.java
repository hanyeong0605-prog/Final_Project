package com.jobpilot.api.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.entity.SubscriptionStatus;
import com.jobpilot.api.domain.subscription.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2026-08-29: 구독 만료 예고/종료 알림. Subscription은 결제 흐름에서만 기간이 채워지는
// 엔티티(setter가 없고 activate()가 항상 "지금부터 1개월"로 잡는다)라, 특정 만료일 상황을
// 만들려면 엔티티를 목으로 두고 조회 결과만 흉내내는 게 가장 단순하다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionExpiryReminderSchedulerTest {
    @Mock private SubscriptionRepository subscriptions;
    @Mock private NotificationLogRepository notificationLogs;
    @Mock private WebPushService webPush;

    private SubscriptionExpiryReminderScheduler scheduler() {
        return new SubscriptionExpiryReminderScheduler(subscriptions, notificationLogs, webPush);
    }

    private Subscription subscription(long id, long memberId, LocalDateTime periodEnd, LocalDateTime canceledAt) {
        Subscription s = mock(Subscription.class);
        when(s.getId()).thenReturn(id);
        when(s.getMemberId()).thenReturn(memberId);
        when(s.getCurrentPeriodEnd()).thenReturn(periodEnd);
        when(s.getCanceledAt()).thenReturn(canceledAt);
        return s;
    }

    private void givenActive(Subscription... found) {
        when(subscriptions.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(found));
        when(subscriptions.findByStatusAndCanceledAtGreaterThanEqual(eq(SubscriptionStatus.CANCELED), any()))
                .thenReturn(List.of());
    }

    private void givenCanceled(Subscription... found) {
        when(subscriptions.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());
        when(subscriptions.findByStatusAndCanceledAtGreaterThanEqual(eq(SubscriptionStatus.CANCELED), any()))
                .thenReturn(List.of(found));
    }

    private NotificationLog capturedLog() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogs).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void remindsThreeDaysBeforeExpiry() {
        givenActive(subscription(1L, 7L, LocalDateTime.now().plusDays(3), null));

        scheduler().sendSubscriptionExpiryNotifications();

        verify(webPush).sendToMember(eq(7L), eq("구독이 3일 뒤 끝나요"), anyString(), eq("/account"));
        assertThat(capturedLog().getNotificationType()).startsWith("SUBSCRIPTION_EXPIRY_D3_");
    }

    @Test
    void doesNotRemindOnOtherDays() {
        // 만료 5일 전 - 예고 대상이 아니다.
        givenActive(subscription(1L, 7L, LocalDateTime.now().plusDays(5), null));

        scheduler().sendSubscriptionExpiryNotifications();

        verify(webPush, never()).sendToMember(anyLong(), anyString(), anyString(), anyString());
        verify(notificationLogs, never()).save(any());
    }

    @Test
    void doesNotResendTheSameReminderWithinOneBillingPeriod() {
        givenActive(subscription(1L, 7L, LocalDateTime.now().plusDays(1), null));
        when(notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);

        scheduler().sendSubscriptionExpiryNotifications();

        verify(webPush, never()).sendToMember(anyLong(), anyString(), anyString(), anyString());
        verify(notificationLogs, never()).save(any());
    }

    @Test
    void reminderTypeIncludesThePeriodSoResubscribersGetNotifiedAgain() {
        // 구독 레코드는 회원당 하나뿐이라 재신청해도 id가 그대로다 - 알림 종류에 만료일이
        // 안 붙어 있으면 다음 달 예고가 지난달 이력에 걸려 영영 안 나간다.
        LocalDateTime periodEnd = LocalDateTime.of(2026, 9, 28, 10, 0);
        Subscription s = mock(Subscription.class);
        when(s.getId()).thenReturn(1L);
        when(s.getMemberId()).thenReturn(7L);
        when(s.getCurrentPeriodEnd()).thenReturn(periodEnd);
        when(s.getCanceledAt()).thenReturn(periodEnd.plusSeconds(1));
        givenCanceled(s);

        scheduler().sendSubscriptionExpiryNotifications();

        assertThat(capturedLog().getNotificationType()).isEqualTo("SUBSCRIPTION_EXPIRED_20260928");
    }

    @Test
    void notifiesWhenTheSubscriptionExpiredOnItsOwn() {
        LocalDateTime periodEnd = LocalDateTime.now().minusHours(8);
        givenCanceled(subscription(1L, 7L, periodEnd, periodEnd.plusMinutes(1)));

        scheduler().sendSubscriptionExpiryNotifications();

        verify(webPush).sendToMember(eq(7L), eq("구독이 종료됐어요"), anyString(), eq("/account"));
    }

    @Test
    void ignoresSubscriptionsTheMemberCanceledThemselves() {
        // 본인이 방금 해지 버튼을 눌렀는데 "끝났어요, 재신청하세요"가 오면 붙잡는 광고로 읽힌다.
        // 직접 해지는 기간이 남아 있는 시점에 일어난다(canceledAt < currentPeriodEnd).
        givenCanceled(subscription(1L, 7L, LocalDateTime.now().plusDays(10), LocalDateTime.now()));

        scheduler().sendSubscriptionExpiryNotifications();

        verify(webPush, never()).sendToMember(anyLong(), anyString(), anyString(), anyString());
        verify(notificationLogs, never()).save(any());
    }

    @Test
    void recordsTheNotificationEvenWhenWebPushIsDisabled() {
        // VAPID 키가 없어 푸시가 못 나가는 환경에서도 앱 안 알림함에는 남아야 한다
        // (WebPushService.sendToMember는 키가 없으면 스스로 no-op이다).
        when(webPush.isEnabled()).thenReturn(false);
        givenActive(subscription(1L, 7L, LocalDateTime.now().plusDays(1), null));

        scheduler().sendSubscriptionExpiryNotifications();

        verify(notificationLogs, times(1)).save(any());
        assertThat(capturedLog().getUrl()).isEqualTo("/account");
    }
}
