package com.jobpilot.api.domain.jobposting.provider.aiserver;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 2026-08-04: 스프링이 ai-server(파이썬)의 크롤링을 "시작해줘"하고 트리거만 하는
 * 클라이언트. 실제 데이터는 여전히 ai-server가 크롤링하면서 기존 방식대로
 * POST /api/v1/job-postings/ingest 로 스프링에 나눠서 push해준다(push 방식 유지).
 *
 * 크롤링 자체가 수십 분 걸리는 배치 작업이라, 이 호출은 background=true로 보내서
 * ai-server가 즉시 {"state":"started"}만 응답하고 실제 작업은 자기 프로세스
 * 안에서 백그라운드로 계속하게 한다 - 이 요청(스프링 쪽 HTTP 커넥션)을 몇십 분씩
 * 붙잡고 있지 않기 위함.
 */
@Component
public class AiServerCrawlClient {
    private final RestClient restClient;
    private final String internalApiKey;

    public AiServerCrawlClient(
            @Value("${app.ai-server.base-url}") String aiServerBaseUrl,
            @Value("${app.internal-api-key:}") String internalApiKey
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(aiServerBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalApiKey = internalApiKey;
    }

    /** 원티드 크롤링을 백그라운드로 시작시킨다. 반환값은 ai-server가 즉시 준 응답(예: {"state":"started"}). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerWantedCrawl() {
        return restClient.post()
                .uri("/crawler/wanted/run?background=true")
                .header("X-Internal-Api-Key", internalApiKey)
                .retrieve()
                .body(Map.class);
    }

    /** 마지막(또는 진행 중인) 크롤링 상태 조회 - 진행 상황 폴링용. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchWantedCrawlStatus() {
        return restClient.get()
                .uri("/crawler/wanted/status")
                .retrieve()
                .body(Map.class);
    }
}
