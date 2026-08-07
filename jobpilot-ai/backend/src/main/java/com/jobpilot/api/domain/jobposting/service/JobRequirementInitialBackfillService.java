package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.dto.JobRequirementBackfillStartResponse;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementBackfillStatusResponse;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionRequest;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionResponse;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementDailyBackfillUsageRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Gradually prepares imported postings with GPT-5.6 Luna. A successfully analyzed posting is excluded
 * from later runs, so the API is never called during ordinary member logins or job searches.
 */
@Service
public class JobRequirementInitialBackfillService {
    private static final Logger log = LoggerFactory.getLogger(JobRequirementInitialBackfillService.class);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Object monitor = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final JobPostingRepository jobPostingRepository;
    private final JobRequirementDailyBackfillUsageRepository dailyUsageRepository;
    private final JobRequirementExtractionService extractionService;
    private final OpenAiJobRequirementClient openAiClient;
    private final TaskExecutor taskExecutor;
    private final boolean enabled;
    private final int dailyLimit;
    private final Duration requestInterval;
    private Progress progress = Progress.idle();

    public JobRequirementInitialBackfillService(
            JobPostingRepository jobPostingRepository,
            JobRequirementDailyBackfillUsageRepository dailyUsageRepository,
            JobRequirementExtractionService extractionService,
            OpenAiJobRequirementClient openAiClient,
            TaskExecutor taskExecutor,
            @Value("${job-requirement-extraction.backfill.enabled:false}") boolean enabled,
            @Value("${job-requirement-extraction.backfill.daily-limit:3000}") int dailyLimit,
            @Value("${job-requirement-extraction.backfill.request-interval-ms:1000}") long requestIntervalMillis
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.dailyUsageRepository = dailyUsageRepository;
        this.extractionService = extractionService;
        this.openAiClient = openAiClient;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.dailyLimit = Math.max(1, dailyLimit);
        this.requestInterval = Duration.ofMillis(Math.max(1_000, requestIntervalMillis));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        startIfEnabled("application startup");
    }

    @Scheduled(cron = "${job-requirement-extraction.backfill.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void startScheduledDailyBackfill() {
        startIfEnabled("daily schedule");
    }

    public JobRequirementBackfillStartResponse start() {
        return startIfEnabled("manual request");
    }

    public JobRequirementBackfillStatusResponse status() {
        Progress current = snapshot();
        return new JobRequirementBackfillStatusResponse(
                running.get(),
                current.totalCandidates(),
                current.processed(),
                current.extracted(),
                current.skipped(),
                current.failed(),
                Math.max(0, current.totalCandidates() - current.processed()),
                current.state(),
                current.message()
        );
    }

    private JobRequirementBackfillStartResponse startIfEnabled(String trigger) {
        if (!enabled) {
            update(Progress.idle("GPT Luna daily backfill is disabled."));
            return new JobRequirementBackfillStartResponse(
                    false, 0, dailyLimit, "GPT Luna daily backfill is disabled."
            );
        }
        if (!openAiClient.isAvailable()) {
            update(Progress.idle("GPT Luna daily backfill is unavailable. Check OPENAI_API_KEY."));
            return new JobRequirementBackfillStartResponse(
                    false, 0, dailyLimit, "GPT Luna daily backfill is unavailable. Check OPENAI_API_KEY."
            );
        }
        if (!running.compareAndSet(false, true)) {
            Progress current = snapshot();
            return new JobRequirementBackfillStartResponse(
                    false, current.totalCandidates(), dailyLimit, "GPT Luna daily backfill is already running."
            );
        }

        LocalDate runDate = LocalDate.now(KOREA_ZONE);
        int alreadyProcessed = dailyUsageRepository.processedCount(runDate);
        if (alreadyProcessed >= dailyLimit) {
            running.set(false);
            String message = "Today's GPT Luna backfill limit has already been reached ("
                    + dailyLimit + " postings).";
            update(Progress.completed(0, 0, 0, 0, message));
            return new JobRequirementBackfillStartResponse(false, 0, dailyLimit, message);
        }

        List<Long> candidateIds;
        try {
            candidateIds = jobPostingRepository.findActiveWithoutRequirementsOrderById().stream()
                    .map(posting -> posting.getId())
                    .toList();
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }

        if (candidateIds.isEmpty()) {
            running.set(false);
            update(Progress.completed(0, 0, 0, 0, "There are no postings left to analyze."));
            return new JobRequirementBackfillStartResponse(
                    false, 0, dailyLimit, "There are no postings left to analyze."
            );
        }

        int selected = Math.min(candidateIds.size(), dailyLimit - alreadyProcessed);
        List<Long> selectedCandidateIds = candidateIds.subList(0, selected);
        update(Progress.running(selected));
        taskExecutor.execute(() -> run(selectedCandidateIds, runDate));
        log.info("Starting GPT Luna job-requirement backfill from {}: up to {} postings ({} used today).",
                trigger, selected, alreadyProcessed);
        return new JobRequirementBackfillStartResponse(
                true, selected, dailyLimit, "GPT Luna daily backfill started in the background."
        );
    }

    private void run(List<Long> candidateIds, LocalDate runDate) {
        try {
            for (Long jobPostingId : candidateIds) {
                if (!dailyUsageRepository.claimSlot(runDate, dailyLimit)) break;
                JobRequirementExtractionResponse response = extractionService.extract(
                        new JobRequirementExtractionRequest(List.of(jobPostingId), 1, false)
                );
                record(response);
                waitForNextRequest();
            }
            Progress current = snapshot();
            update(Progress.completed(
                    current.totalCandidates(), current.extracted(), current.skipped(), current.failed(),
                    "GPT Luna daily backfill completed."
            ));
        } catch (OpenAiQuotaExhaustedException exception) {
            Progress current = snapshot();
            log.warn("GPT Luna job-requirement backfill paused because OpenAI credit is unavailable.");
            update(new Progress(
                    current.totalCandidates(), current.processed(), current.extracted(), current.skipped(),
                    current.failed(), "PAUSED", "GPT Luna credit is unavailable. Add credit, then wait for the next schedule or restart the server."
            ));
        } catch (RuntimeException exception) {
            Progress current = snapshot();
            log.error("GPT Luna daily job-requirement backfill stopped unexpectedly.", exception);
            update(new Progress(
                    current.totalCandidates(), current.processed(), current.extracted(), current.skipped(),
                    current.failed(), "FAILED", "GPT Luna daily backfill stopped. It will retry remaining postings tomorrow."
            ));
        } finally {
            running.set(false);
        }
    }

    private void waitForNextRequest() {
        try {
            Thread.sleep(requestInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GPT Luna daily backfill was interrupted.", exception);
        }
    }

    private void record(JobRequirementExtractionResponse response) {
        synchronized (monitor) {
            progress = new Progress(
                    progress.totalCandidates(),
                    progress.processed() + response.selected(),
                    progress.extracted() + response.extracted(),
                    progress.skipped() + response.skipped(),
                    progress.failed() + response.failed(),
                    "RUNNING",
                    "GPT Luna daily backfill is running."
            );
        }
    }

    private void update(Progress next) {
        synchronized (monitor) {
            progress = next;
        }
    }

    private Progress snapshot() {
        synchronized (monitor) {
            return progress;
        }
    }

    private record Progress(
            int totalCandidates,
            int processed,
            int extracted,
            int skipped,
            int failed,
            String state,
            String message
    ) {
        static Progress idle() {
            return idle("GPT Luna daily backfill has not started.");
        }

        static Progress idle(String message) {
            return new Progress(0, 0, 0, 0, 0, "IDLE", message);
        }

        static Progress running(int totalCandidates) {
            return new Progress(totalCandidates, 0, 0, 0, 0, "RUNNING", "GPT Luna daily backfill is running.");
        }

        static Progress completed(int totalCandidates, int extracted, int skipped, int failed, String message) {
            return new Progress(totalCandidates, totalCandidates, extracted, skipped, failed, "COMPLETED", message);
        }
    }
}
