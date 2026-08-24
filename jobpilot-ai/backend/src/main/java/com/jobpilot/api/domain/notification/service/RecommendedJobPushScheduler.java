package com.jobpilot.api.domain.notification.service;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.matching.entity.JobMatch;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.repository.JobMatchRepository;
import com.jobpilot.api.domain.member.entity.ConsentType;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberConsentRepository;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.global.mail.JobADreamMailService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<RecommendationLevel, String> TYPES = Map.of(
            RecommendationLevel.APPLY_NOW, "RECOMMENDED",
            RecommendationLevel.CHALLENGE_AFTER_GAPS, "RECOMMENDED_CHALLENGE");
    // 하루 새 매칭이 여러 건이어도 한 분류당 3건을 넘기지 않는다.
    private static final int MAX_NOTIFICATIONS_PER_LEVEL_PER_MEMBER = 3;

    private final JobMatchRepository jobMatches;
    private final JobPostingRepository jobPostings;
    private final NotificationLogRepository notificationLogs;
    private final WebPushService webPush;
    private final MemberRepository members;
    private final MemberConsentRepository consents;
    private final JobADreamMailService mail;

    public RecommendedJobPushScheduler(
            JobMatchRepository jobMatches,
            JobPostingRepository jobPostings,
            NotificationLogRepository notificationLogs,
            WebPushService webPush, MemberRepository members,
            MemberConsentRepository consents, JobADreamMailService mail
    ) {
        this.jobMatches = jobMatches;
        this.jobPostings = jobPostings;
        this.notificationLogs = notificationLogs;
        this.webPush = webPush;
        this.members = members;
        this.consents = consents;
        this.mail = mail;
    }

    /**
     * 매일 오전 8시 30분(KST) - 크롤러(06:00)와 요건추출 백필(10분 간격) 이후, 마감임박
     * 알림(09:00)보다 먼저 실행되게 잡았다. 최근 24시간 안에 새로 APPLY_NOW로 분석된
     * JobMatch 중, 아직 알림을 안 보낸 것만 골라 발송한다.
     */
    @Scheduled(cron = "${push.recommended-job.cron:0 30 8 * * *}", zone = "Asia/Seoul")
    public void sendRecommendedJobPush() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Map<Long, Map<RecommendationLevel, List<JobPosting>>> emailCandidates = new HashMap<>();
        Map<String, Integer> sentPerMemberAndLevel = new HashMap<>();
        int totalSent = 0;
        for (RecommendationLevel level : TYPES.keySet()) {
            for (JobMatch match : jobMatches.findByRecommendationLevelAndAnalyzedAtAfterOrderByReadinessScoreDesc(level, since)) {
                Long memberId = match.getMemberId();
                String limitKey = memberId + ":" + level.name();
                if (sentPerMemberAndLevel.getOrDefault(limitKey, 0) >= MAX_NOTIFICATIONS_PER_LEVEL_PER_MEMBER) continue;
                Long jobId = match.getJobPostingId();
                String type = TYPES.get(level);
                if (notificationLogs.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(memberId, JOB, jobId, type)) continue;
                JobPosting job = jobPostings.findById(jobId).orElse(null);
                if (job == null) continue;
                String companyPrefix = job.getCompanyName() == null ? "" : job.getCompanyName() + " · ";
                String title = level == RecommendationLevel.APPLY_NOW ? "지금 바로 지원 가능한 새 공고예요" : "보완 후 도전해 볼 새 공고예요";
                String body = companyPrefix + job.getTitle();
                String url = "/job-postings/" + job.getId();
                // 인앱 알림은 웹푸시 설정과 무관하게 항상 기록한다.
                notificationLogs.save(new NotificationLog(memberId, JOB, jobId, type, title, body, url));
                webPush.sendToMember(memberId, title, body, url);
                emailCandidates.computeIfAbsent(memberId, ignored -> new EnumMap<>(RecommendationLevel.class))
                        .computeIfAbsent(level, ignored -> new ArrayList<>()).add(job);
                sentPerMemberAndLevel.merge(limitKey, 1, Integer::sum);
                totalSent++;
            }
        }
        for (Map.Entry<Long, Map<RecommendationLevel, List<JobPosting>>> entry : emailCandidates.entrySet()) {
            Member member = members.findById(entry.getKey()).orElse(null);
            boolean agreed = consents.findByMemberIdAndConsentType(entry.getKey(), ConsentType.MARKETING_EMAIL)
                    .map(consent -> consent.isAgreed()).orElse(false);
            if (member == null || !agreed) continue;
            try { mail.sendRecommendedJobs(member, entry.getValue()); }
            catch (RuntimeException exception) { log.warn("맞춤공고 이메일 발송 실패 (memberId={})", entry.getKey(), exception); }
        }
        if (totalSent > 0) log.info("추천 공고 알림 {}건 발송", totalSent);
    }
}
