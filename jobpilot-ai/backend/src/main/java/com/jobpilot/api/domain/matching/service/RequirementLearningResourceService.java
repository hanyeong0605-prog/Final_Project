package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.book.service.AladinBookService;
import com.jobpilot.api.domain.matching.dto.GrowthResourceRecommendationResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.service.QnetQualificationService;
import com.jobpilot.api.domain.opportunity.entity.Opportunity;
import com.jobpilot.api.domain.opportunity.repository.OpportunityRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Finds concrete catalogue items for one missing requirement; no AI call is made here. */
@Service
public class RequirementLearningResourceService {
    private final QnetQualificationService qualifications;
    private final OpportunityRepository opportunities;
    private final AladinBookService books;

    public RequirementLearningResourceService(QnetQualificationService qualifications,
            OpportunityRepository opportunities, AladinBookService books) {
        this.qualifications = qualifications; this.opportunities = opportunities; this.books = books;
    }

    public List<GrowthResourceRecommendationResponse> recommend(String requirement, String requirementType, Long requirementId) {
        String keyword = keyword(requirement);
        java.util.ArrayList<GrowthResourceRecommendationResponse> result = new java.util.ArrayList<>();
        if ("CERTIFICATION".equals(requirementType)) {
            result.add(certificate(requirement, keyword).orElseGet(() -> new GrowthResourceRecommendationResponse("CERTIFICATE", "자격증",
                    (keyword.isBlank() ? requirement : keyword) + " 관련 자격증 탐색", "등록된 자격증 카탈로그에서 요구 요건과 연결되는 종목을 찾아보세요.",
                    "/opportunities?category=CERTIFICATE&resourceQuery=" + URLEncoder.encode(keyword.isBlank() ? requirement : keyword, StandardCharsets.UTF_8))));
            return List.copyOf(result);
        }
        if (keyword.isBlank()) return List.of();
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        result.add(training(keyword, encoded).orElseGet(() -> new GrowthResourceRecommendationResponse("TRAINING", "고용24",
                keyword + " 훈련과정 탐색", "현재 등록된 고용24 훈련과정에서 " + keyword + " 키워드로 비교해 보세요.",
                "/opportunities?category=TRAINING&resourceQuery=" + encoded)));
        result.add(book(keyword, encoded, requirementId).orElseGet(() -> new GrowthResourceRecommendationResponse("BOOK", "도서",
                keyword + " 학습 도서 탐색", "관련 도서와 실습 예제를 찾아 학습 계획에 추가해 보세요.",
                "/opportunities?category=BOOK&resourceQuery=" + encoded)));
        return List.copyOf(result);
    }

    private java.util.Optional<GrowthResourceRecommendationResponse> certificate(String requirement, String keyword) {
        try {
            return qualifications.catalogSnapshot().stream()
                    .sorted(Comparator.comparingInt((QnetQualificationResponse item) -> certificateScore(item, requirement, keyword)).reversed())
                    .filter(item -> certificateScore(item, requirement, keyword) >= 20).findFirst()
                    .map(item -> {
                        String query = URLEncoder.encode(item.name(), StandardCharsets.UTF_8);
                        String focus = keyword.isBlank() ? requirement : keyword;
                        return new GrowthResourceRecommendationResponse("CERTIFICATE", "자격증", item.name(),
                                item.field() + " · " + focus + " 요건에 직접 연결되는 자격 종목",
                                "/opportunities?category=CERTIFICATE&resourceQuery=" + query);
                    });
        } catch (Exception ignored) { return java.util.Optional.empty(); }
    }

    private java.util.Optional<GrowthResourceRecommendationResponse> training(String keyword, String encoded) {
        return opportunities.findByStatusOrderByDeadlineAtAsc("ACTIVE").stream()
                .filter(item -> "교육".equals(item.getType()))
                .sorted(Comparator.comparingInt((Opportunity item) -> trainingScore(item, keyword)).reversed())
                .filter(item -> trainingScore(item, keyword) > 0).findFirst()
                .map(item -> new GrowthResourceRecommendationResponse("TRAINING", "고용24", item.getTitle(),
                        (item.getOrganization() == null ? "고용24 훈련과정" : item.getOrganization()) + " · " + keyword + " 학습 과정", "/opportunities/" + item.getId()));
    }

    private java.util.Optional<GrowthResourceRecommendationResponse> book(String keyword, String encoded, Long requirementId) {
        try {
            return books.search(null, requirementId, keyword, "ALL", "relevance", 0, 3).items().stream()
                    .filter(item -> bookScore(item.title() + " " + item.description() + " " + item.category(), keyword) > 0)
                    .findFirst()
                    .map(item -> new GrowthResourceRecommendationResponse("BOOK", "도서", item.title(),
                            join(item.author(), item.publisher()), item.link().isBlank() ? "/opportunities?category=BOOK&resourceQuery=" + encoded : item.link()));
        } catch (Exception ignored) { return java.util.Optional.empty(); }
    }

    private int certificateScore(QnetQualificationResponse item, String requirement, String keyword) {
        String text = normalized(item.name() + " " + item.field() + " " + item.subField());
        String requested = normalized(requirement);
        if (text.contains(requested) || requested.contains(normalized(item.name()))) return 100;
        if (keyword.contains("데이터") && (text.contains("데이터") || text.contains("빅데이터"))) return 30;
        if (keyword.contains("보안") && text.contains("보안")) return 30;
        if (keyword.contains("네트워크") && (text.contains("네트워크") || text.contains("통신"))) return 30;
        return 0;
    }
    private int trainingScore(Opportunity item, String keyword) {
        String text = normalized(item.getTitle() + " " + item.getDescription() + " " + item.getTrainingContents());
        int score = 0;
        for (String token : keyword.split(" ")) if (token.length() > 1 && text.contains(normalized(token))) score += 12;
        return score;
    }
    private int bookScore(String text, String keyword) {
        String normalizedText = normalized(text);
        int score = 0;
        for (String token : keyword.split(" ")) if (token.length() > 1 && normalizedText.contains(normalized(token))) score += 12;
        return score;
    }
    private String keyword(String text) {
        String lower = normalized(text);
        if (lower.contains("rag") || lower.contains("검색증강")) return "RAG";
        if (lower.contains("prompt") || lower.contains("프롬프트") || lower.contains("structuredoutput") || lower.contains("structured output")) return "LLM 프롬프트";
        if (lower.contains("api") || lower.contains("연동")) return "API 연동";
        if (lower.contains("spring")) return "Spring";
        if (lower.contains("java")) return "Java";
        if (lower.contains("python")) return "Python";
        if (lower.contains("react")) return "React";
        if (lower.contains("aws") || lower.contains("cloud") || lower.contains("docker") || lower.contains("컨테이너")) return "AWS Docker";
        if (lower.contains("sql") || lower.contains("데이터") || lower.contains("db")) return "SQL 데이터";
        if (lower.contains("ai") || lower.contains("llm") || lower.contains("rag")) return "AI LLM";
        if (lower.contains("보안") || lower.contains("네트워크")) return "정보보안 네트워크";
        return "";
    }
    private String normalized(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    private String join(String a, String b) { return java.util.stream.Stream.of(a, b).filter(value -> value != null && !value.isBlank()).reduce((x, y) -> x + " · " + y).orElse("알라딘 도서"); }
}
