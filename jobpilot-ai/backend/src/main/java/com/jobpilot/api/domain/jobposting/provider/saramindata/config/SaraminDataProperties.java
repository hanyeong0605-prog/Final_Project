package com.jobpilot.api.domain.jobposting.provider.saramindata.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saramin-data")
public record SaraminDataProperties(
        boolean enabled,
        String accessKey,
        String baseUrl,
        String jobMidCode,
        String jobType,
        int countPerPage,
        int maxPages,
        boolean crawlEnabled,
        long crawlDelayMs,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public void requireUsable() {
        if (!enabled) throw new IllegalStateException("SaraminDATA가 비활성화되어 있습니다.");
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalStateException("SARAMIN_ACCESS_KEY 환경변수가 필요합니다.");
        }
        URI endpoint = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || !"oapi.saramin.co.kr".equalsIgnoreCase(endpoint.getHost())) {
            throw new IllegalStateException("SaraminDATA API 주소는 공식 HTTPS 호스트만 사용할 수 있습니다.");
        }
        if (countPerPage < 1 || countPerPage > 110) {
            throw new IllegalStateException("사람인 API count-per-page는 1~110이어야 합니다.");
        }
        if (maxPages < 1) throw new IllegalStateException("SaraminDATA max-pages는 1 이상이어야 합니다.");
    }
}
