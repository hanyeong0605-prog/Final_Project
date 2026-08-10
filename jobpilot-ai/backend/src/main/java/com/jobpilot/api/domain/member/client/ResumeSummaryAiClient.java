package com.jobpilot.api.domain.member.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 2026-08-10: 태스크 #63 "반영" 방향 - 회원이 자기소개서/프로젝트를 저장할 때마다 ai-server의
 * POST /resume/technical-summary/synthesize를 불러서 새 기술 요약을 받아온다.
 * jobposting/provider/aiserver/AiServerCrawlClient.java와 같은 패턴(RestClient +
 * X-Internal-Api-Key 헤더) - ai-server가 크롤링 트리거를 받을 때 쓰는 것과 같은 내부 키를
 * 재사용한다(InternalApiKeyFilter/ai-server의 INTERNAL_API_KEY와 동일).
 *
 * ResumeCareerSyncService가 이 클라이언트 호출 실패(ai-server 다운, 타임아웃 등)를
 * try/catch로 감싸서 fail-open 처리한다 - 이력서 저장 자체가 요약 반영 실패 때문에
 * 실패해서는 안 된다.
 */
@Component
public class ResumeSummaryAiClient {
    private final RestClient restClient;
    private final String internalApiKey;

    public ResumeSummaryAiClient(
            @Value("${app.ai-server.base-url}") String aiServerBaseUrl,
            @Value("${app.internal-api-key:}") String internalApiKey
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20)); // Gemini 호출 포함이라 크롤링 트리거보다 여유를 둔다
        this.restClient = RestClient.builder()
                .baseUrl(aiServerBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalApiKey = internalApiKey;
    }

    public record ProjectContent(String title, String roleDescription, String problemDescription,
                                  String solutionDescription, String resultDescription) {}

    /** 합성 결과를 그대로 돌려준다 - {"ok": bool, "message": String|null, "summary": String|null}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> synthesizeTechnicalSummary(
            String job, String existingSummary, List<String> selfIntroductions, List<ProjectContent> projects
    ) {
        List<Map<String, String>> projectPayload = projects.stream()
                .map(p -> Map.of(
                        "title", nullToEmpty(p.title()),
                        "role_description", nullToEmpty(p.roleDescription()),
                        "problem_description", nullToEmpty(p.problemDescription()),
                        "solution_description", nullToEmpty(p.solutionDescription()),
                        "result_description", nullToEmpty(p.resultDescription())))
                .toList();

        return restClient.post()
                .uri("/resume/technical-summary/synthesize")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(Map.of(
                        "job", nullToEmpty(job),
                        "existing_summary", nullToEmpty(existingSummary),
                        "self_introductions", selfIntroductions,
                        "projects", projectPayload))
                .retrieve()
                .body(Map.class);
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
