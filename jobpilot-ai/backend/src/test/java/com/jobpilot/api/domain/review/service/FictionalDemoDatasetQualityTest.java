package com.jobpilot.api.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class FictionalDemoDatasetQualityTest {
    @Test
    void providesOneHundredDistinctBrandsAndPostings() {
        assertThat(FictionalDemoDataset.NAMES).hasSize(100).doesNotHaveDuplicates();

        List<String> descriptions = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            var sector = FictionalDemoDataset.SECTORS.get(index / 10);
            var role = FictionalDemoDataset.ROLES.get(index % 10);
            String company = FictionalDemoDataset.NAMES.get(index) + " (가상기업)";
            descriptions.add(FictionalDemoDataset.description(index, company, sector, role));
        }

        assertThat(new HashSet<>(descriptions)).hasSize(100);
        assertThat(descriptions).allSatisfy(text -> {
            assertThat(text).contains("[가상기업 안내]", "[주요 업무]", "[자격 요건]", "[업무 환경과 복지]", "[채용 절차]");
            assertThat(text.length()).isGreaterThan(1_050);
        });
    }

    @Test
    void providesFiveContextualReviewsForEveryPosting() {
        List<String> reviewContents = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            var sector = FictionalDemoDataset.SECTORS.get(index / 10);
            var role = FictionalDemoDataset.ROLES.get(index % 10);
            String company = FictionalDemoDataset.NAMES.get(index) + " (가상기업)";
            for (int ordinal = 0; ordinal < 5; ordinal++) {
                var review = FictionalDemoDataset.review(index, ordinal, company, sector, role);
                reviewContents.add(String.join("\n", review.title(), review.pros(), review.cons(), review.body(), review.managementMessage()));
                assertThat(review.pros().split("\\n")).hasSizeGreaterThanOrEqualTo(3);
                assertThat(review.cons().split("\\n")).hasSizeGreaterThanOrEqualTo(3);
                assertThat(review.managementMessage()).isNotBlank();
            }
        }

        assertThat(reviewContents).hasSize(500);
        assertThat(new HashSet<>(reviewContents)).hasSize(500);
    }
}
