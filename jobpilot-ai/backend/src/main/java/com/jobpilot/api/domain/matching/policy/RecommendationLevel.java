package com.jobpilot.api.domain.matching.policy;

/** 사람인 공고 대비 지원 준비 단계이며 합격 확률이나 최종 자격 판정이 아니다. */
public enum RecommendationLevel {
    DIFFICULT_NOW,
    CHALLENGE_AFTER_GAPS,
    APPLY_NOW
}
