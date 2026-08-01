package com.jobpilot.api.domain.matching.policy;

/**
 * LLM의 코멘트 생성과 분리된, 재현 가능한 V1 등급 정책이다.
 * 실제 매칭 서비스는 공고 요건별 DIRECT / RELATED 근거를 집계해 이 정책에 전달한다.
 */
public final class MatchPolicy {
    private MatchPolicy() {}

    public static RecommendationLevel determine(MatchInput input) {
        if (input.requiredCount() == 0 || input.hasExplicitBlocker()) {
            return RecommendationLevel.DIFFICULT_NOW;
        }

        int covered = input.directEvidenceCount() + input.relatedEvidenceCount();
        int minimumForReview = (int) Math.ceil(input.requiredCount() * 0.5d);
        if (covered < minimumForReview) {
            return RecommendationLevel.DIFFICULT_NOW;
        }

        int directThreshold = (int) Math.ceil(input.requiredCount() * 0.75d);
        if (input.directEvidenceCount() >= directThreshold && covered >= input.requiredCount()) {
            return RecommendationLevel.APPLY_NOW;
        }

        return RecommendationLevel.CHALLENGE_AFTER_GAPS;
    }

    public record MatchInput(int requiredCount, int directEvidenceCount, int relatedEvidenceCount, boolean hasExplicitBlocker) {
        public MatchInput {
            if (requiredCount < 0 || directEvidenceCount < 0 || relatedEvidenceCount < 0) {
                throw new IllegalArgumentException("Evidence counts cannot be negative.");
            }
        }
    }
}
