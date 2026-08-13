package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.interest.entity.UserInterest;
import com.jobpilot.api.domain.interest.repository.UserInterestRepository;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// 2026-08-13: 찜한 공고 중 마감이 D-3/D-1로 다가온 것을 매일 아침 훑어서 웹푸시로 알려준다.
// InterestService.toggle()이 이미 찜한 공고를 PlannerEvent(마감일 캘린더)로 넣어주고
// 있어서, 그 "찜한 공고" 목록(UserInterest, targetType=JOB_POSTING)을 그대로 재사용한다 -
// 새 "알림 구독" 개념을 따로 만들지 않았다.
@Service
public class DeadlineReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);
    private static final String JOB = "JOB_POSTING";
    private static final List<Integer> REMINDER_DAYS_BEFORE = List.of(3, 1);

    private final UserInterestRepository interests;
    private final JobPostingRepository jobs;
    private final NotificationLogRepository notificationLogs;
    private final WebPushService webPush;

    public DeadlineReminderScheduler(
            UserInterestRepository interests,
            JobPostingRepository jobs,
            NotificationLogRepository notificationLogs,
            WebPushService webPush
    ) {
        this.interests = interests;
        this.jobs = jobs;
        this.notificationLogs = notificationLogs;
        this.webPush = webPush;
    }

    /**
     * 매일 오전 9시(KST) - 찜한 공고 중 마감 D-3/D-1인 것을 찾아 발송한다.
     * NotificationLog로 회원+공고+"D-며칠" 조합별 발송 여부를 기록해서 같은 알림이
     * 다음날 다시 도는 스케줄러 실행에서 중복 발송되지 않게 막는다.
     */
    @Scheduled(cron = "${push.deadline-reminder.cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void sendDeadlineReminders() {
        if (!webPush.isEnabled()) return; // VAPID 키가 없으면 어차피 못 보내니 스캔 자체를 생략
        LocalDate today = LocalDate.now();
        int sentCount = 0;
        for (UserInterest bookmark : interests.findByTargetType(JOB)) {
            JobPosting job = jobs.findById(bookmark.getTargetId()).orElse(null);
            // 상시채용(rollingDeadline)이거나 마감일 정보가 없는 공고는 "마감임박" 자체가 성립하지 않는다.
            if (job == null || job.getDeadlineAt() == null || job.isRollingDeadline()) continue;

            long daysLeft = ChronoUnit.DAYS.between(today, job.getDeadlineAt().toLocalDate());
            if (!REMINDER_DAYS_BEFORE.contains((int) daysLeft)) continue;

            String notificationType = "DEADLINE_REMINDER_D" + daysLeft;
            boolean alreadySent = notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                    bookmark.getMemberId(), JOB, job.getId(), notificationType);
            if (alreadySent) continue;

            String companyPrefix = job.getCompanyName() == null ? "" : job.getCompanyName() + " · ";
            webPush.sendToMember(
                    bookmark.getMemberId(),
                    "마감 " + daysLeft + "일 전이에요",
                    companyPrefix + job.getTitle(),
                    "/job-postings/" + job.getId());
            notificationLogs.save(new NotificationLog(bookmark.getMemberId(), JOB, job.getId(), notificationType));
            sentCount++;
        }
        if (sentCount > 0) log.info("마감임박 알림 {}건 발송", sentCount);
    }
}
