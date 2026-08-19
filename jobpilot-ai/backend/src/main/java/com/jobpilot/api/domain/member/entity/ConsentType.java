package com.jobpilot.api.domain.member.entity;

public enum ConsentType {
    TERMS_OF_SERVICE,
    PRIVACY_COLLECTION,
    MARKETING_EMAIL,
    /** Explicit, optional permission to send resume text and structured career facts to the AI provider. */
    RESUME_AI_PROCESSING
}
