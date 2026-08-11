package com.jobpilot.api.domain.member.dto;

/** 한국방송통신전파진흥원(KCA) 국가기술자격증(개인) 진위여부 확인 결과. */
public record CertificateAuthenticityResponse(boolean genuine) {}
