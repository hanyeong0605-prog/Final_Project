package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.repository.JobMatchRepository;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.domain.notification.repository.PushSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// 2026-08-13: 맞춤 신규 공고 추천 알림(2단계) - 새로운 매칭 로직을 만들지 않고, 이미 있는
// JobMatch(스킬/경력/자격증 기반 적합도 판정, JobMatchGenerationService 참고)를 그대로
// 재사용한다. 이 스케줄러가 하는 일은 딱 하나 - "최근에 새로 APPLY_NOW로 분석된 매칭 중
// 아직 알림 안 보낸 것"을 찾아 푸시로 알려주는 것뿐이다. JobMatch는 공고가 새로 분석될
// 때마다(JobRequirementExtractionService.regenerateForPosting) 매 회원에 대해 갱신되므로
// "새 공고가 크롤링 -> 요건 추출 -> 매칭 계산"까지 이미 자동으로 흘러가고, 여기서는 그
// 결과만 훑는다.
@Service
public class RecommendedJobPushScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecommendedJobPushScheduler.class);
    private static final String JOB = "JOB_POSTING";
    private static final String NOTIFICATION_TYPE = "RECOMMENDED";
    // 하루 새 매칭이 여러 건이어도 한 회원에게 알림 폭탄을 보내지 않도록 상한을 둔다.
    private static final int MAX_NOTIFICATIONS_PER_MEMBER_PER_RUN = 3;

    private final JobMatchRepository jobMatches;
    private final JobPostingRepository jobPostings;
    private final PushSubscriptionRepository pushSubscriptions;
    private final NotificationLogRepository notificationLogs;
    private final WebPushService webPush;

    public RecommendedJobPushScheduler(
            JobMatchRepository jobMatches,
            JobPostingRepository jobPostings,
            PushSubscriptionRepository pushSubscriptions,
            NotificationLogRepository notificationLogs,
            WebPushService webPush
    ) {
        this.jobMatches = jobMatches;
        this.jobPostings = jobPostings;
        this.pushSubscriptions = pushSubscriptions;
        this.notificationLogs = notificationLogs;
        this.webPush = webPush;
    }

    /**
     * 매일 오전 8시 30분(KST) - 크롤러(06:00)와 요건추출 백필(10분 간격) 이후, 마감임박
     * 알림(09:00)보다 먼저 실행되게 잡았다. 최근 24시간 안에 새로 APPLY_NOW로 분석된
     * JobMatch 중, 아직 알림을 안 보낸 것만 골라 발송한다.
     */
    @Scheduled(cron = "${push.recommended-job.cron:0 30 8 * * *}", zone = "Asia/Seoul")
    public void sendRecommendedJobPush() {
        if (!webPush.isEnabled()) return;

        Set<Long> pushEnabledMembers = new HashSet<>(pushSubscriptions.findDistinctMemberIds());
        if (pushEnabledMembers.isEmpty()) return; // 알림 켠 회원이 아무도 없으면 매칭 조회할 필요조차 없다.

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        var freshMatches = jobMatches.findByRecommendationLevelAndAnalyzedAtAfterOrderByReadinessScoreDesc(
                RecommendationLevel.APPLY_NOW, since);

        Map<Long, Integer> sentPerMember = new HashMap<>();
        int totalSent = 0;
        for (JobMatch match : freshMatches) {
            Long memberId = match.getMemberId();
            if (!pushEnabledMembers.contains(memberId)) continue;
            if (sentPerMember.getOrDefault(memberId, 0) >= MAX_NOTIFICATIONS_PER_MEMBER_PER_RUN) continue;

            Long jobId = match.getJobPostingId();
            boolean alreadySent = notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
                    memberId, JOB, jobId, NOTIFICATION_TYPE);
            if (alreadySent) continue;

            JobPosting job = jobPostings.findById(jobId).orElse(null);
            if (job == null) continue;

            String companyPrefix = job.getCompanyName() == null ? "" : job.getCompanyName() + " · ";
            webPush.sendToMember(
                    memberId,
                    "지금 바로 지원 가능한 공고예요",
                    companyPrefix + job.getTitle(),
                    "/job-postings/" + job.getId());
            notificationLogs.save(new NotificationLog(memberId, JOB, jobId, NOTIFICATION_TYPE));

            sentPerMember.merge(memberId, 1, Integer::sum);
            totalSent++;
        }
        if (totalSent > 0) log.info("추천 공고 알림 {}건 발송", totalSent);
    }
}
