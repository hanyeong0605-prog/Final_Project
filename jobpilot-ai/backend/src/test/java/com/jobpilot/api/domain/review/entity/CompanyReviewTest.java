package com.jobpilot.api.domain.review.entity;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;

class CompanyReviewTest {
    private CompanyReview review() {
        return CompanyReview.byMember(1L, 10L, 100L, "리뷰어", 4, "제목", "동료가 좋아요", "야근이 있어요", "후기입니다");
    }

    @Test void newReviewIsPendingNotNeutral() {
        var r = review();
        assertThat(r.getAnalysisState()).isEqualTo("PENDING");
        assertThat(r.getSourceType()).isEqualTo("USER");
        assertThat(r.getContentHash()).hasSize(64);
    }

    @Test void changedTextInvalidatesAnalysisAndRejectsLateResponse() {
        var r = review();
        String oldHash = r.getContentHash();
        assertThat(r.completeAnalysis(oldHash)).isTrue();
        r.edit(100L, 2, "제목", "동료가 좋아요", "야근이 많아요", "다른 후기");
        assertThat(r.getAnalysisState()).isEqualTo("PENDING");
        assertThat(r.completeAnalysis(oldHash)).isFalse();
        assertThat(r.getAnalysisState()).isEqualTo("PENDING");
    }

    @Test void ratingAloneDoesNotChangeEmotionInput() {
        var r = review();
        String hash = r.getContentHash();
        r.completeAnalysis(hash);
        r.edit(100L, 1, r.getTitle(), r.getPros(), r.getCons(), r.getBody());
        assertThat(r.getContentHash()).isEqualTo(hash);
        assertThat(r.getAnalysisState()).isEqualTo("COMPLETED");
    }

    @Test void ownershipAndDeletionAreEnforced() {
        var r = review();
        assertThatThrownBy(() -> r.deleteByMember(101L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> r.edit(101L, 1, "제목", "장점", "단점", "후기")).isInstanceOf(AccessDeniedException.class);
        r.deleteByMember(100L);
        assertThat(r.completeAnalysis(r.getContentHash())).isFalse();
        assertThatThrownBy(() -> r.edit(100L, 1, "제목", "장점", "단점", "후기"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void invalidInputDoesNotPartiallyMutateEntity() {
        var r = review();
        assertThatIllegalArgumentException().isThrownBy(() -> r.edit(100L, 1, "제목", "장점", "단점", "가".repeat(5000)));
        assertThat(r.getRating()).isEqualTo(4);
        assertThatIllegalArgumentException().isThrownBy(() -> r.edit(100L, 6, "제목", "장점", "단점", "후기"));
    }
}
