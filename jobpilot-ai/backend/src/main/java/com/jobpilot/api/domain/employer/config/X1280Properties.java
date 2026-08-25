package com.jobpilot.api.domain.employer.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "x1280")
public record X1280Properties(boolean enabled, URI baseUrl, String serverKey) {
    public X1280Properties {
        if (enabled) {
            if (baseUrl == null) throw new IllegalStateException("X1280_BASE_URL이 필요합니다.");
            if (serverKey == null || serverKey.isBlank()) {
                throw new IllegalStateException("X1280_SERVER_KEY가 필요합니다.");
            }
        }
    }
}
