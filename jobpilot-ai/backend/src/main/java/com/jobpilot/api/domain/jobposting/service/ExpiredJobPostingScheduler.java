package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredJobPostingScheduler {
    private final JobPostingRepository postings;

    public ExpiredJobPostingScheduler(JobPostingRepository postings) {
        this.postings = postings;
    }

    /** Keeps expired postings out of normal lists while retaining their administration history. */
    @Transactional
    @Scheduled(cron = "${job-posting.expiry.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void closeExpiredPostings() {
        postings.closeExpiredActive(LocalDateTime.now());
    }
}
