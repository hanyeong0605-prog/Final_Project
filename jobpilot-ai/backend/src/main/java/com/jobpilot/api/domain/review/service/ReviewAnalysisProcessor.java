package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import org.springframework.stereotype.Service;

/** Not transactional: the HTTP call must never hold a DB connection or row lock. */
@Service
public class ReviewAnalysisProcessor {
    private final ReviewAnalysisStore store;
    private final SentimentAiClient ai;
    public ReviewAnalysisProcessor(ReviewAnalysisStore store, SentimentAiClient ai) { this.store = store; this.ai = ai; }

    public boolean processOne() {
        var claimed = store.claim();
        if (claimed.isEmpty()) return false;
        var work = claimed.get();
        try {
            var result = ai.analyze(work.text());
            if (result.isPresent() && work.hash().equals(result.get().contentHash())) store.complete(work, result.get());
            else store.fail(work);
        } catch (RuntimeException ex) {
            // A failed write leaves a recoverable lease. No original text goes to logs.
            store.fail(work);
        }
        return true;
    }
}
