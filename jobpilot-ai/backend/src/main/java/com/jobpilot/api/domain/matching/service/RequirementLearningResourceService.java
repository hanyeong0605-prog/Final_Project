package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.book.dto.AladinBookResponse;
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

    public List<GrowthResourceRecommendationResponse> recommend(String requirement, Long requirementId) {
        String keyword = keyword(requirement);
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        java.util.ArrayList<GrowthResourceRecommendationResponse> result = new java.util.ArrayList<>();
        certificate(keyword, encoded).ifPresent(result::add);
        training(keyword, encoded).ifPresent(result::add);
        book(keyword, encoded, requirementId).ifPresent(result::add);
        return List.copyOf(result);
    }

    private java.util.Optional<GrowthResourceRecommendationResponse> certificate(String keyword, String encoded) {
        try {
            return qualifications.catalogSnapshot().stream()
                    .sorted(Comparator.comparingInt((QnetQualificationResponse item) -> certificateScore(item, keyword)).reversed())
                    .filter(item -> certificateScore(item, keyword) > 0).findFirst()
                    .map(item -> new GrowthResourceRecommendationResponse("CERTIFICATE", "자격증", item.name(),
                            item.field() + " · " + keyword + " 보강에 연결되는 자격 종목", "/opportunities?category=CERTIFICATE&resourceQuery=" + encoded));
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
            return books.search(null, requirementId, keyword, "ALL", "relevance", 0, 3).items().stream().findFirst()
                    .map(item -> new GrowthResourceRecommendationResponse("BOOK", "도서", item.title(),
                            join(item.author(), item.publisher()), item.link().isBlank() ? "/opportunities?category=BOOK&resourceQuery=" + encoded : item.link()));
        } catch (Exception ignored) { return java.util.Optional.empty(); }
    }

    private int certificateScore(QnetQualificationResponse item, String keyword) {
        String text = normalized(item.name() + " " + item.field() + " " + item.subField());
        int score = contains(text, keyword) ? 8 : 0;
        if (isTech(keyword) && text.contains("정보통신")) score += 3;
        if (keyword.contains("데이터") && (text.contains("데이터") || text.contains("빅데이터"))) score += 8;
        if (keyword.contains("보안") && text.contains("보안")) score += 8;
        if (keyword.contains("네트워크") && text.contains("통신")) score += 5;
        if (keyword.contains("API") && text.contains("정보처리")) score += 7;
        return score;
    }
    private int trainingScore(Opportunity item, String keyword) {
        String text = normalized(item.getTitle() + " " + item.getDescription() + " " + item.getTrainingContents());
        int score = contains(text, keyword) ? 20 : 0;
        for (String token : keyword.split(" ")) if (token.length() > 1 && text.contains(normalized(token))) score += 5;
        return score;
    }
    private String keyword(String text) {
        String lower = normalized(text);
        if (lower.contains("prompt") || lower.contains("프롬프트") || lower.contains("structuredoutput")) return "LLM 프롬프트 엔지니어링";
        if (lower.contains("api") || lower.contains("연동")) return "API 연동";
        if (lower.contains("spring")) return "Spring";
        if (lower.contains("java")) return "Java";
        if (lower.contains("python")) return "Python";
        if (lower.contains("react")) return "React";
        if (lower.contains("aws") || lower.contains("cloud") || lower.contains("docker") || lower.contains("컨테이너")) return "AWS Docker";
        if (lower.contains("sql") || lower.contains("데이터") || lower.contains("db")) return "SQL 데이터";
        if (lower.contains("ai") || lower.contains("llm") || lower.contains("rag")) return "AI LLM";
        if (lower.contains("보안") || lower.contains("네트워크")) return "정보보안 네트워크";
        return "IT 개발";
    }
    private boolean isTech(String keyword) { return true; }
    private boolean contains(String text, String keyword) { return text.contains(normalized(keyword)); }
    private String normalized(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
    private String join(String a, String b) { return java.util.stream.Stream.of(a, b).filter(value -> value != null && !value.isBlank()).reduce((x, y) -> x + " · " + y).orElse("알라딘 도서"); }
}
