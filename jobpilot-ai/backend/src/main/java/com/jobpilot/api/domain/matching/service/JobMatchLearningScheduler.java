package com.jobpilot.api.domain.matching.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuild once after the daily crawl without adding an external ML service. */
@Component
public class JobMatchLearningScheduler {
    private final JobMatchLearningClient learningClient;

    public JobMatchLearningScheduler(JobMatchLearningClient learningClient) {
        this.learningClient = learningClient;
    }

    @Scheduled(cron = "${job-match-learning.retrain-cron:0 20 6 * * *}", zone = "Asia/Seoul")
    public void retrainAfterDailyCrawl() {
        learningClient.retrain();
    }
}
