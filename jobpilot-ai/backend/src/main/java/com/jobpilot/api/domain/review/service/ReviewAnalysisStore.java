package com.jobpilot.api.domain.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.sentiment.client.SentimentAiClient.Analysis;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Short DB transactions only. Optimistic version claims work with multiple backend instances. */
@Repository
public class ReviewAnalysisStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ReviewAnalysisStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public record Work(long reviewId, long version, String hash, String text, int attempts) {}

    @Transactional
    public Optional<Work> claim() {
        // A crash on the fifth attempt must not leave PROCESSING displayed forever.
        jdbc.update("""
                UPDATE company_reviews SET analysis_state='FAILED',next_analysis_at=NULL,version=version+1
                WHERE analysis_state='PROCESSING' AND analysis_attempts>=5 AND next_analysis_at<=CURRENT_TIMESTAMP
                """);
        var candidates = jdbc.query("""
                SELECT id,version,content_hash,title,pros,cons,body,analysis_attempts FROM company_reviews
                WHERE visibility='PUBLIC' AND analysis_state IN ('PENDING','FAILED','PROCESSING')
                  AND analysis_attempts < 5 AND next_analysis_at <= CURRENT_TIMESTAMP
                ORDER BY next_analysis_at,id LIMIT 1
                """, (rs, row) -> new Work(rs.getLong("id"), rs.getLong("version"), rs.getString("content_hash"),
                String.join("\n", rs.getString("title"), rs.getString("pros"), rs.getString("cons"), rs.getString("body")),
                rs.getInt("analysis_attempts") + 1));
        if (candidates.isEmpty()) return Optional.empty();
        Work work = candidates.getFirst();
        // next_analysis_at doubles as a two-minute lease. A crashed process can be retried later.
        int changed = jdbc.update("""
                UPDATE company_reviews SET analysis_state='PROCESSING',analysis_attempts=analysis_attempts+1,
                  next_analysis_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 2 MINUTE),version=version+1
                WHERE id=? AND version=? AND visibility='PUBLIC'
                """, work.reviewId(), work.version());
        return changed == 1 ? Optional.of(new Work(work.reviewId(), work.version() + 1, work.hash(), work.text(), work.attempts()))
                : Optional.empty();
    }

    @Transactional
    public boolean complete(Work work, Analysis result) {
        if (!work.hash().equals(result.contentHash())) return false;
        String emotions;
        try { emotions = mapper.writeValueAsString(result.emotions()); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid analysis structure", ex); }
        int changed = jdbc.update("""
                UPDATE company_reviews SET analysis_state='COMPLETED',next_analysis_at=NULL,version=version+1
                WHERE id=? AND version=? AND content_hash=? AND visibility='PUBLIC' AND analysis_state='PROCESSING'
                """, work.reviewId(), work.version(), work.hash());
        if (changed == 0) return false; // A newer edit/deletion/worker owns this row now.
        jdbc.update("""
                INSERT INTO company_review_analyses(review_id,content_hash,model_version,policy_version,
                  polarity,positive_score,neutral_score,negative_score,emotions)
                VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                """, work.reviewId(), work.hash(), result.modelVersion(), result.policyVersion(),
                result.polarity().label(), result.polarity().positive(), result.polarity().neutral(),
                result.polarity().negative(), emotions);
        return true;
    }

    @Transactional
    public void fail(Work work) {
        LocalDateTime next = work.attempts() >= 5 ? null : LocalDateTime.now().plusSeconds(30L << (work.attempts() - 1));
        jdbc.update("""
                UPDATE company_reviews SET analysis_state='FAILED',next_analysis_at=?,version=version+1
                WHERE id=? AND version=? AND content_hash=? AND visibility='PUBLIC' AND analysis_state='PROCESSING'
                """, next, work.reviewId(), work.version(), work.hash());
    }

    @Transactional(readOnly = true)
    public Optional<Analysis> latest(Long reviewId) {
        return jdbc.query("""
                SELECT a.* FROM company_review_analyses a JOIN company_reviews r ON r.id=a.review_id
                WHERE r.id=? AND r.visibility='PUBLIC' AND r.analysis_state='COMPLETED'
                  AND r.content_hash=a.content_hash ORDER BY a.analyzed_at DESC,a.id DESC LIMIT 1
                """, (rs, row) -> {
            try {
                var emotions = mapper.readValue(rs.getString("emotions"),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.jobpilot.api.domain.sentiment.client.SentimentAiClient.Emotion>>() {});
                return new Analysis(rs.getString("model_version"), rs.getString("policy_version"),
                        rs.getString("content_hash"), emotions,
                        new com.jobpilot.api.domain.sentiment.client.SentimentAiClient.Polarity(
                                rs.getString("polarity"), rs.getDouble("positive_score"),
                                rs.getDouble("neutral_score"), rs.getDouble("negative_score")));
            } catch (JsonProcessingException ex) {
                throw new java.sql.SQLException("Stored sentiment format is invalid");
            }
        }, reviewId).stream().findFirst();
    }
}
