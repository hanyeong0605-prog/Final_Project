package com.jobpilot.api.global.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedMember {
    private AuthenticatedMember() {}

    public static Long id(Authentication authentication) {
        // 2026-08-19: 기업회원(EMPLOYER) 토큰이 구직자/관리자 전용 API에 그대로 쓰이면
        // employer.id와 member.id가 우연히 같은 숫자일 때 다른 사람 계정으로 인증되는
        // 사고가 날 수 있어 명시적으로 막는다. JwtTokenService.issueForEmployer가 심는
        // "actorType" 클레임 참고 - 일반 회원 토큰에는 이 클레임이 없다.
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && "EMPLOYER".equals(jwtAuth.getToken().getClaimAsString("actorType"))) {
            throw new AccessDeniedException("기업회원 계정으로는 접근할 수 없습니다.");
        }
        return Long.parseLong(authentication.getName());
    }
}
