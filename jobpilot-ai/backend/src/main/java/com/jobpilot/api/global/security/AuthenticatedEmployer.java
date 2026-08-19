package com.jobpilot.api.global.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** AuthenticatedMember의 기업회원 버전 - "actorType":"EMPLOYER" 클레임이 있는 토큰만 통과시킨다. */
public final class AuthenticatedEmployer {
    private AuthenticatedEmployer() {}

    public static Long id(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !"EMPLOYER".equals(jwtAuth.getToken().getClaimAsString("actorType"))) {
            throw new AccessDeniedException("기업회원 전용 API입니다.");
        }
        return Long.parseLong(authentication.getName());
    }
}
