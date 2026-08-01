package com.jobpilot.api.domain.matching.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchPolicyTest {
    @Test
    void directEvidenceForAllRequiredItemsIsReadyToApply() {
        var input = new MatchPolicy.MatchInput(4, 4, 0, false);
        assertThat(MatchPolicy.determine(input)).isEqualTo(RecommendationLevel.APPLY_NOW);
    }

    @Test
    void partialEvidenceNeedsImprovementWhenHalfIsCovered() {
        var input = new MatchPolicy.MatchInput(4, 2, 1, false);
        assertThat(MatchPolicy.determine(input)).isEqualTo(RecommendationLevel.CHALLENGE_AFTER_GAPS);
    }

    @Test
    void explicitBlockerIsInsufficientEvidence() {
        var input = new MatchPolicy.MatchInput(4, 4, 0, true);
        assertThat(MatchPolicy.determine(input)).isEqualTo(RecommendationLevel.DIFFICULT_NOW);
    }
}
