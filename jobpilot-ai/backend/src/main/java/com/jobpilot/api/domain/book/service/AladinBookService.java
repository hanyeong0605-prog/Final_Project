package com.jobpilot.api.domain.book.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.book.dto.AladinBookPageResponse;
import com.jobpilot.api.domain.book.dto.AladinBookResponse;
import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Aladin TTB search proxy. The TTB key never leaves this server. */
@Service
public class AladinBookService {
    private static final String ITEM_SEARCH_URL = "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx";
    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;
    private static final Map<String, String> CATEGORY_KEYWORDS = Map.ofEntries(
            Map.entry("IT", "프로그래밍"), Map.entry("DATA_AI", "데이터 인공지능"),
            Map.entry("CLOUD", "클라우드 Docker Kubernetes"), Map.entry("SECURITY", "정보보안"),
            Map.entry("CAREER", "IT 취업"), Map.entry("LANGUAGE", "영어"), Map.entry("BUSINESS", "비즈니스"));
    private static final List<String> TECH_KEYWORDS = List.of(
            "Spring Boot", "Spring", "Java", "Python", "React", "TypeScript", "JavaScript", "Node.js",
            "MySQL", "SQL", "Oracle", "AWS", "Docker", "Kubernetes", "Linux", "네트워크", "보안",
            "클라우드", "인공지능", "머신러닝", "딥러닝", "데이터", "빅데이터", "프로그래밍");

    private final String ttbKey;
    private final JobRequirementRepository requirementRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public AladinBookService(@Value("${aladin.ttb-key:}") String ttbKey,
            JobRequirementRepository requirementRepository, ObjectMapper objectMapper) {
        this.ttbKey = ttbKey == null ? "" : ttbKey.trim();
        this.requirementRepository = requirementRepository;
        this.objectMapper = objectMapper;
    }

    public AladinBookPageResponse search(Long jobPostingId, Long requirementId, String query, String category,
            String sort, int page, int size) {
        if (ttbKey.isBlank()) throw new IllegalStateException("알라딘 API 키가 설정되지 않았습니다.");
        int safeSize = Math.min(Math.max(size <= 0 ? DEFAULT_SIZE : size, 1), MAX_SIZE);
        int safePage = Math.max(page, 0);
        RequirementContext context = requirementContext(jobPostingId, requirementId);
        String requested = query == null ? "" : query.trim();
        String normalizedCategory = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        String recommendationKeyword = requested.isBlank()
                ? CATEGORY_KEYWORDS.getOrDefault(normalizedCategory, context.keyword())
                : requested;
        String apiSort = switch (sort == null ? "relevance" : sort.toLowerCase(Locale.ROOT)) {
            case "title" -> "Title";
            case "published" -> "PublishTime";
            case "rating" -> "CustomerRating";
            default -> "Accuracy";
        };
        URI uri = UriComponentsBuilder.fromUriString(ITEM_SEARCH_URL)
                .queryParam("ttbkey", ttbKey).queryParam("Query", recommendationKeyword)
                .queryParam("QueryType", "Keyword").queryParam("SearchTarget", "Book")
                .queryParam("MaxResults", safeSize).queryParam("start", safePage + 1)
                .queryParam("Sort", apiSort).queryParam("output", "js").queryParam("Version", "20131101")
                .build().encode().toUri();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.USER_AGENT, "JobPilot/1.0");
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode body = objectMapper.readTree(response.getBody());
            List<AladinBookResponse> items = new ArrayList<>();
            for (JsonNode item : body.path("item")) items.add(toBook(item));
            int total = body.path("totalResults").asInt(items.size());
            return new AladinBookPageResponse(List.copyOf(items), (safePage + 1) * safeSize < total, total,
                    recommendationKeyword, context.evidence());
        } catch (Exception exception) {
            throw new IllegalStateException("알라딘 도서 정보를 조회하지 못했습니다.");
        }
    }

    private RequirementContext requirementContext(Long jobPostingId, Long requirementId) {
        JobRequirement requirement = requirementId == null ? null : requirementRepository.findById(requirementId).orElse(null);
        if (requirement != null && jobPostingId != null && !jobPostingId.equals(requirement.getJobPostingId())) requirement = null;
        if (requirement == null && jobPostingId != null) {
            requirement = requirementRepository.findByJobPostingId(jobPostingId).stream().findFirst().orElse(null);
        }
        String evidence = requirement == null ? "" : firstNonBlank(requirement.getContent(), requirement.getSourceExcerpt());
        String lower = evidence.toLowerCase(Locale.ROOT);
        String keyword = TECH_KEYWORDS.stream().filter(item -> lower.contains(item.toLowerCase(Locale.ROOT)))
                .findFirst().orElse("프로그래밍");
        return new RequirementContext(keyword, evidence.length() > 180 ? evidence.substring(0, 180) + "…" : evidence);
    }

    private AladinBookResponse toBook(JsonNode item) {
        String title = item.path("title").asText("").replaceAll("<[^>]+>", "");
        String description = item.path("description").asText("");
        String category = item.path("categoryName").asText("");
        List<String> tags = tags(title + " " + description + " " + category);
        return new AladinBookResponse(item.path("isbn13").asText(item.path("isbn").asText()), title,
                item.path("author").asText(""), item.path("publisher").asText(""), item.path("pubDate").asText(""),
                item.path("cover").asText(""), item.path("link").asText(""), description, category,
                item.path("priceStandard").asInt(0), item.path("customerReviewRank").asDouble(0), tags);
    }

    private List<String> tags(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return TECH_KEYWORDS.stream().filter(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT))).limit(5).toList();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private record RequirementContext(String keyword, String evidence) {}
}
