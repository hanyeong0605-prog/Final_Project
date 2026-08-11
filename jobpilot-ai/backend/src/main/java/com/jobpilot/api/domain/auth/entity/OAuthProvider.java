package com.jobpilot.api.domain.auth.entity;

public enum OAuthProvider {
    GOOGLE, KAKAO, NAVER;

    public static OAuthProvider fromRegistrationId(String value) {
        return OAuthProvider.valueOf(value.toUpperCase());
    }
}
