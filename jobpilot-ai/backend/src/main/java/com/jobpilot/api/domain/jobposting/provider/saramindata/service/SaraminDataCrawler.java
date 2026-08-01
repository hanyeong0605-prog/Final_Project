package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.jobpilot.api.domain.jobposting.provider.saramindata.config.SaraminDataProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class SaraminDataCrawler {
    private static final String USER_AGENT = "JobPilotAI-SaraminDATA/1.0 (+provider integration)";
    private final SaraminDataProperties properties;

    public SaraminDataCrawler(SaraminDataProperties properties) { this.properties = properties; }

    public CrawlResult crawl(String sourceUrl) {
        if (!properties.crawlEnabled()) return CrawlResult.notRequested();
        String crawlUrl = validateAndSecureUrl(sourceUrl);
        try {
            Document document = Jsoup.connect(crawlUrl)
                    .userAgent(USER_AGENT)
                    .timeout(properties.readTimeoutSeconds() * 1000)
                    .followRedirects(true)
                    .get();
            Element content = first(document,
                    ".user_content", ".jv_cont", "[data-view-content]", ".wrap_jv_cont");
            if (content == null) return CrawlResult.failed();

            String description = content.text().replaceAll("\\s+", " ").trim();
            if (description.length() > 100_000) description = description.substring(0, 100_000);
            LinkedHashSet<String> excerpts = new LinkedHashSet<>();
            for (Element element : content.select("li, p, dt, dd")) {
                String text = element.text().replaceAll("\\s+", " ").trim();
                if (text.length() >= 4 && text.length() <= 500 && looksLikeRequirement(text)) excerpts.add(text);
                if (excerpts.size() == 30) break;
            }
            return new CrawlResult(description, new ArrayList<>(excerpts), "SUCCESS");
        } catch (Exception exception) {
            return CrawlResult.failed();
        }
    }

    private Element first(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null && !element.text().isBlank()) return element;
        }
        return null;
    }

    private boolean looksLikeRequirement(String text) {
        return text.contains("자격") || text.contains("필수") || text.contains("우대")
                || text.contains("경험") || text.contains("가능") || text.contains("보유");
    }

    private String validateAndSecureUrl(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        if (!http || host == null
                || !(host.equals("saramin.co.kr") || host.endsWith(".saramin.co.kr"))) {
            throw new IllegalArgumentException("사람인 원문만 크롤링할 수 있습니다.");
        }
        return "http".equalsIgnoreCase(uri.getScheme())
                ? URI.create(value.replaceFirst("^http://", "https://")).toString()
                : value;
    }

    public record CrawlResult(String description, List<String> requirementExcerpts, String status) {
        static CrawlResult notRequested() { return new CrawlResult("", List.of(), "NOT_REQUESTED"); }
        static CrawlResult failed() { return new CrawlResult("", List.of(), "FAILED"); }
    }
}
