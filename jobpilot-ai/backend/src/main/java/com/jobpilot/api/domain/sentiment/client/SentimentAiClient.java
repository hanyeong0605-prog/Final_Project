package com.jobpilot.api.domain.sentiment.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * KOTE로 직접 학습한 내부 모델 호출 경계. 게시판 글과 댓글은 이 추론을 공유하고
 * 저장 대상, 조회 권한, 통계는 각각 분리한다. 점수는 독립값이지 합계 100% 비율이 아니다.
 * 호출은 콘텐츠 저장 트랜잭션 밖에서 실행하고 실패 시 PENDING/FAILED를 유지해야 한다.
 * Optional.empty()를 중립 감정으로 바꾸거나 별점으로 분석 결과를 만들어서는 안 된다.
 */
@Component
public class SentimentAiClient {
    private static final Set<String> POLARITIES = Set.of("POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED");
    private final RestClient client;
    private final String key;

    @Autowired
    public SentimentAiClient(@Value("${app.ai-server.base-url}") String baseUrl,
                             @Value("${app.internal-api-key:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key;
    }

    // Package-visible constructor lets contract tests use a mock HTTP server without external AI.
    SentimentAiClient(RestClient client, String key) {
        this.client = client;
        this.key = key;
    }

    public record Emotion(String label, Double score) {}
    public record Polarity(String label, Double positive, Double neutral, Double negative) {}
    public record Analysis(String modelVersion, String policyVersion, String contentHash,
                           List<Emotion> emotions, Polarity polarity) {}

    public Optional<Analysis> analyze(String text) {
        // Python strip differs from Java strip for some Unicode whitespace. Trim using Python's
        // whitespace definition so the hash describes exactly what the inference server receives.
        String normalized = normalize(text);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 5000) {
            throw new IllegalArgumentException("감정분석 원문은 1~5000자여야 합니다.");
        }
        if (key == null || key.isBlank()) return Optional.empty();
        try {
            Analysis result = client.post().uri("/sentiment/analyze")
                    .header("X-Internal-Api-Key", key)
                    .body(Map.of("text", normalized, "topK", 5))
                    .retrieve().body(Analysis.class);
            return valid(result, contentHash(normalized)) ? Optional.of(result) : Optional.empty();
        } catch (org.springframework.web.client.RestClientException ex) {
            // Do not log original community content, internal keys or upstream response bodies.
            return Optional.empty();
        }
    }

    public static String contentHash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String normalize(String text) {
        if (text == null) return "";
        int start = 0, end = text.length();
        while (start < end && whitespace(text.codePointAt(start))) start += Character.charCount(text.codePointAt(start));
        while (end > start && whitespace(text.codePointBefore(end))) end -= Character.charCount(text.codePointBefore(end));
        return text.substring(start, end);
    }

    private static boolean whitespace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85;
    }

    private static boolean valid(Analysis a, String hash) {
        if (a == null || blank(a.modelVersion()) || blank(a.policyVersion()) || !hash.equals(a.contentHash())
                || a.emotions() == null || a.emotions().size() > 5 || a.polarity() == null) return false;
        Polarity p = a.polarity();
        return p.label() != null && POLARITIES.contains(p.label()) && score(p.positive()) && score(p.neutral())
                && score(p.negative()) && a.emotions().stream().allMatch(e -> e != null && !blank(e.label()) && score(e.score()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean score(Double value) { return value != null && Double.isFinite(value) && value >= 0 && value <= 1; }
}
