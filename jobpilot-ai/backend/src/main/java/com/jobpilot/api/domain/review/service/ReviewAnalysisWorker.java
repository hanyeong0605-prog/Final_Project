package com.jobpilot.api.domain.review.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Enable only after migration + model deployment with APP_SENTIMENT_WORKER_ENABLED=true.
 * A separate daemon prevents slow AI calls from delaying existing reminder/billing schedulers.
 */
@Component
@ConditionalOnProperty(name = "app.sentiment.worker.enabled", havingValue = "true")
public class ReviewAnalysisWorker {
    private static final Logger log = LoggerFactory.getLogger(ReviewAnalysisWorker.class);
    private final ReviewAnalysisProcessor processor;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "company-review-analysis");
        thread.setDaemon(true);
        return thread;
    });
    public ReviewAnalysisWorker(ReviewAnalysisProcessor processor) { this.processor = processor; }
    @PostConstruct void start() { executor.scheduleWithFixedDelay(this::runBatch, 10, 10, TimeUnit.SECONDS); }
    private void runBatch() {
        try {
            for (int count = 0; count < 10 && !Thread.currentThread().isInterrupted(); count++)
                if (!processor.processOne()) break;
        } catch (RuntimeException ex) {
            log.warn("Review analysis batch deferred; database or worker unavailable");
        }
    }
    @PreDestroy void stop() { executor.shutdownNow(); }
}
