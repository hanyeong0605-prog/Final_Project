package com.jobpilot.api.domain.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Opt-in integration checks against scripts/test-review-schema.ps1's disposable local DB only. */
@EnabledIfSystemProperty(named = "review.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:33379/review_schema_[a-f0-9]+.*")
class ReviewAnalysisMysqlTest {
    private JdbcTemplate jdbc;
    private ReviewAnalysisStore store;
    private TransactionTemplate tx;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource(System.getProperty("review.mysql.url"), "root", "");
        jdbc = new JdbcTemplate(ds);
        String directory = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        assertThat(directory.toLowerCase()).startsWith("c:/final_project/tmp/review-schema-mysql-");
        store = new ReviewAnalysisStore(jdbc, new ObjectMapper());
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("DELETE a FROM company_review_analyses a JOIN company_reviews r ON r.id=a.review_id WHERE r.seed_key LIKE 'INTEGRATION-%'");
        jdbc.update("DELETE FROM company_reviews WHERE seed_key LIKE 'INTEGRATION-%'");
    }

    private ReviewAnalysisStore.Work claimNew() {
        String seed = "INTEGRATION-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO company_reviews(company_id,seed_key,source_type,display_author,rating,title,pros,cons,body,
                  content_hash,next_analysis_at) VALUES (1,?,'SYNTHETIC_DEMO','Test',4,'T','P','C','B',?,CURRENT_TIMESTAMP)
                """, seed, SentimentAiClient.contentHash("T\nP\nC\nB"));
        var claimed = tx.execute(status -> store.claim().orElseThrow());
        assertThat(jdbc.queryForObject("SELECT seed_key FROM company_reviews WHERE id=?", String.class, claimed.reviewId())).isEqualTo(seed);
        return claimed;
    }
    private SentimentAiClient.Analysis result(ReviewAnalysisStore.Work w) {
        return new SentimentAiClient.Analysis("test-model", "test-policy", w.hash(), List.of(),
                new SentimentAiClient.Polarity("MIXED", .8, .1, .6));
    }

    @Test void claimCompleteAndReadCurrentResult() {
        var work = claimNew();
        assertThat(work.attempts()).isEqualTo(1);
        var secondClaim = tx.execute(status -> store.claim());
        assertThat(secondClaim).isEmpty();
        Boolean completed = tx.execute(status -> store.complete(work, result(work)));
        assertThat(completed).isTrue();
        assertThat(store.latest(work.reviewId()).orElseThrow().modelVersion()).isEqualTo("test-model");
        jdbc.update("UPDATE company_reviews SET content_hash=REPEAT('b',64),analysis_state='PENDING',version=version+1 WHERE id=?", work.reviewId());
        assertThat(store.latest(work.reviewId())).isEmpty();
    }

    @Test void editedReviewRejectsOldWorker() {
        var work = claimNew();
        jdbc.update("UPDATE company_reviews SET content_hash=REPEAT('c',64),analysis_state='PENDING',version=version+1 WHERE id=?", work.reviewId());
        Boolean completed = tx.execute(status -> store.complete(work, result(work)));
        assertThat(completed).isFalse();
        tx.executeWithoutResult(status -> store.fail(work));
        assertThat(jdbc.queryForObject("SELECT analysis_state FROM company_reviews WHERE id=?", String.class, work.reviewId())).isEqualTo("PENDING");
    }

    @Test void unavailableModelSchedulesRetry() {
        var work = claimNew();
        tx.executeWithoutResult(status -> store.fail(work));
        assertThat(jdbc.queryForObject("SELECT analysis_state FROM company_reviews WHERE id=?", String.class, work.reviewId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT next_analysis_at>CURRENT_TIMESTAMP FROM company_reviews WHERE id=?", Boolean.class, work.reviewId())).isTrue();
        assertThat(store.latest(work.reviewId())).isEmpty();
    }

    @Test void finalCrashedAttemptDoesNotStayProcessingForever() {
        var work = claimNew();
        jdbc.update("UPDATE company_reviews SET analysis_attempts=5,next_analysis_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE id=?", work.reviewId());
        tx.execute(status -> store.claim());
        assertThat(jdbc.queryForObject("SELECT analysis_state FROM company_reviews WHERE id=?", String.class, work.reviewId())).isEqualTo("FAILED");
    }
}
