package com.jobpilot.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 2026-08-04: ai-server(파이썬 크롤러)가 POST /api/v1/job-postings/ingest 를 호출할 때
 * 쓰는 서버 간(server-to-server) 인증. 사용자 로그인 JWT와는 별개의 개념이라 OAuth2
 * resource server 필터 대신 이 필터로 따로 처리한다.
 *
 * 원래 이 경로가 SecurityConfig에서 GET만 permitAll이고 POST는 인증이 필요한 상태였는데,
 * 크롤러는 로그인 토큰이 없어서 매번 401로 조용히 실패하고 있었다(2026-08-04 발견 -
 * 2836건 크롤링 성공했는데 DB엔 0건 저장된 버그의 원인). 사용자 계정용 로그인을 굳이
 * 만들 필요는 없는 요청이라, 공유 비밀키(.env의 INTERNAL_API_KEY) 하나로 검증하는
 * 가벼운 방식을 택했다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Internal-Api-Key";
    private static final String PROTECTED_PATH = "/api/v1/job-postings/ingest";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${app.internal-api-key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean isProtectedIngestCall = HttpMethod.POST.matches(request.getMethod()) && PROTECTED_PATH.equals(request.getRequestURI());

        if (isProtectedIngestCall) {
            String providedKey = request.getHeader(HEADER_NAME);
            boolean keyConfigured = expectedApiKey != null && !expectedApiKey.isBlank();
            boolean keyMatches = keyConfigured && expectedApiKey.equals(providedKey);
            if (!keyMatches) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"INVALID_INTERNAL_API_KEY\",\"message\":\"내부 API 키가 없거나 올바르지 않습니다.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
