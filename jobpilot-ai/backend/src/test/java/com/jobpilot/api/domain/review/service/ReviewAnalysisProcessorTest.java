package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewAnalysisProcessorTest {
    private final ReviewAnalysisStore store = mock(ReviewAnalysisStore.class);
    private final SentimentAiClient ai = mock(SentimentAiClient.class);
    private final ReviewAnalysisProcessor processor = new ReviewAnalysisProcessor(store, ai);
    private final ReviewAnalysisStore.Work work = new ReviewAnalysisStore.Work(1, 3, "hash", "후기", 1);
    private SentimentAiClient.Analysis result(String hash) {
        return new SentimentAiClient.Analysis("model", "policy", hash, List.of(),
                new SentimentAiClient.Polarity("MIXED", .7, .2, .8));
    }
    @Test void emptyQueueDoesNotCallAi() {
        when(store.claim()).thenReturn(Optional.empty());
        assertThat(processor.processOne()).isFalse();
        verifyNoInteractions(ai);
    }
    @Test void storesActualResult() {
        var result = result("hash");
        when(store.claim()).thenReturn(Optional.of(work));
        when(ai.analyze("후기")).thenReturn(Optional.of(result));
        assertThat(processor.processOne()).isTrue();
        verify(store).complete(work, result);
        verify(store, never()).fail(any());
    }
    @Test void missingModelRetriesWithoutInventingNeutral() {
        when(store.claim()).thenReturn(Optional.of(work));
        when(ai.analyze("후기")).thenReturn(Optional.empty());
        processor.processOne();
        verify(store).fail(work);
        verify(store, never()).complete(any(), any());
    }
    @Test void wrongContentHashIsNotSaved() {
        when(store.claim()).thenReturn(Optional.of(work));
        when(ai.analyze("후기")).thenReturn(Optional.of(result("other")));
        processor.processOne();
        verify(store).fail(work);
        verify(store, never()).complete(any(), any());
    }
    @Test void staleCompletionIsNotRetriedAgainstNewContent() {
        var result = result("hash");
        when(store.claim()).thenReturn(Optional.of(work));
        when(ai.analyze("후기")).thenReturn(Optional.of(result));
        when(store.complete(work, result)).thenReturn(false);
        processor.processOne();
        verify(store, never()).fail(any());
    }
}
