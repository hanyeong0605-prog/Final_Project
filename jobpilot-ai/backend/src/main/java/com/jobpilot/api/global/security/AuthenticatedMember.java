package com.jobpilot.api.global.security;

import org.springframework.security.core.Authentication;

public final class AuthenticatedMember {
    private AuthenticatedMember() {}

    public static Long id(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
