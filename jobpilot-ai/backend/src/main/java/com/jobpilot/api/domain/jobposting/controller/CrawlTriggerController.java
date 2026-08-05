package com.jobpilot.api.domain.jobposting.controller;

import com.jobpilot.api.domain.jobposting.provider.aiserver.AiServerCrawlClient;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2026-08-04: 사람이 매번 ai-server에 curl로 크롤링을 트리거하지 않아도 되도록,
 * 스프링을 통해서도 크롤링을 시작시킬 수 있게 하는 관리용 엔드포인트.
 * 로그인한 사용자만 호출 가능(SecurityConfig의 anyRequest().authenticated() 적용).
 */
@RestController
@RequestMapping("/api/v1/admin/crawl")
public class CrawlTriggerController {
    private final AiServerCrawlClient aiServerCrawlClient;

    public CrawlTriggerController(AiServerCrawlClient aiServerCrawlClient) {
        this.aiServerCrawlClient = aiServerCrawlClient;
    }

    @PostMapping("/wanted")
    public Map<String, Object> triggerWantedCrawl() {
        return aiServerCrawlClient.triggerWantedCrawl();
    }

    @GetMapping("/wanted/status")
    public Map<String, Object> wantedCrawlStatus() {
        return aiServerCrawlClient.fetchWantedCrawlStatus();
    }
}
