package com.jobpilot.api.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.repository.SubscriptionRepository;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 2026-08-29: 매달 무료 이용권 지급 + 알림. 지급 여부가 곧 알림 여부라 엔티티를 목이 아니라
// 실물로 쓴다 - grantMonthlyFreeIfEligible의 멱등성이 이 스케줄러 동작의 핵심이기 때문이다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewPassNoticeSchedulerTest {
    @Mock private SubscriptionRepository subscriptions;
    @Mock private NotificationLogRepository notificationLogs;
    @Mock private WebPushService webPush;

    private InterviewPassNoticeScheduler scheduler() {
        return new InterviewPassNoticeScheduler(subscriptions, notificationLogs, webPush);
    }

    private Subscription pass(long memberId) {
        return new Subscription(memberId, "member-" + memberId, "five", 5_900);
    }

    private NotificationLog capturedLog() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogs).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void grantsOneFreeSessionAndTellsHowManyAreLeft() {
        Subscription subscription = pass(7L);
        when(subscriptions.findAll()).thenReturn(List.of(subscription));

        scheduler().grantAndAnnounceMonthlyFreePass();

        assertThat(subscription.getRemainingSessions()).isEqualTo(1);
        verify(webPush).sendToMember(7L, "이번 달 무료 이용권이 도착했어요",
                "지금 실전면접을 1회 이용할 수 있어요.", "/mock-interview");
    }

    @Test
    void countsPurchasedSessionsInTheMessage() {
        // 산 게 남아 있으면 무료 1회가 더해진 총합을 알려줘야 한다.
        Subscription subscription = pass(7L);
        subscription.addSessions("five", 5_900, 5);
        when(subscriptions.findAll()).thenReturn(List.of(subscription));

        scheduler().grantAndAnnounceMonthlyFreePass();

        assertThat(subscription.getRemainingSessions()).isEqualTo(6);
        verify(webPush).sendToMember(anyLong(), anyString(),
                org.mockito.ArgumentMatchers.contains("6회"), anyString());
    }

    @Test
    void skipsMembersWhoAlreadyGotThisMonthsFreePass() {
        // 이미 이 달에 받은 사람(사이트에 먼저 들어와 lazy로 받은 경우)은 건너뛴다.
        Subscription subscription = pass(7L);
        subscription.grantMonthlyFreeIfEligible(YearMonth.now().toString());
        when(subscriptions.findAll()).thenReturn(List.of(subscription));

        scheduler().grantAndAnnounceMonthlyFreePass();

        assertThat(subscription.getRemainingSessions()).isEqualTo(1); // 이중 지급 없음
        verify(webPush, never()).sendToMember(anyLong(), anyString(), anyString(), anyString());
        verify(notificationLogs, never()).save(any());
    }

    @Test
    void doesNotSendTwiceIfTheBatchIsRerun() {
        when(subscriptions.findAll()).thenReturn(List.of(pass(7L)));
        when(notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                anyLong(), anyString(), any(), anyString())).thenReturn(true);

        scheduler().grantAndAnnounceMonthlyFreePass();

        verify(webPush, never()).sendToMember(anyLong(), anyString(), anyString(), anyString());
        verify(notificationLogs, never()).save(any());
    }

    @Test
    void notificationTypeCarriesTheMonthSoNextMonthIsNotBlocked() {
        when(subscriptions.findAll()).thenReturn(List.of(pass(7L)));

        scheduler().grantAndAnnounceMonthlyFreePass();

        assertThat(capturedLog().getNotificationType()).isEqualTo("FREE_PASS_" + YearMonth.now());
    }

    @Test
    void recordsTheNoticeEvenWhenWebPushIsOff() {
        // VAPID 키가 없어 푸시가 안 나가도 앱 안 알림함에는 남아야 한다.
        when(webPush.isEnabled()).thenReturn(false);
        when(subscriptions.findAll()).thenReturn(List.of(pass(7L)));

        scheduler().grantAndAnnounceMonthlyFreePass();

        assertThat(capturedLog().getUrl()).isEqualTo("/mock-interview");
    }
}
