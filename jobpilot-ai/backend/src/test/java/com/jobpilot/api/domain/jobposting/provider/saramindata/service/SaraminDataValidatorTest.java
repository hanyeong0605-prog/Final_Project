package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.*;
import org.junit.jupiter.api.Test;

class SaraminDataValidatorTest {
    private final SaraminDataValidator validator = new SaraminDataValidator();

    @Test
    void acceptsOfficialHttpSourceUrlAndRequiredFields() {
        assertThat(validator.invalidReason(job("http://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1")))
                .isEmpty();
    }

    @Test
    void rejectsNonSaraminSourceUrl() {
        assertThat(validator.invalidReason(job("https://example.com/jobs/1")))
                .contains("사람인 원문 URL이 올바르지 않습니다.");
    }

    @Test
    void rejectsDeadlineBeforeOpening() {
        Job original = job("https://www.saramin.co.kr/jobs/1");
        Job invalid = new Job(original.id(), original.url(), original.active(), original.company(),
                original.position(), original.keyword(), original.salary(), original.postingTimestamp(),
                original.modificationTimestamp(), "200", "100", original.closeType());
        assertThat(validator.invalidReason(invalid)).contains("마감일이 공고 시작일보다 빠릅니다.");
    }

    private Job job(String url) {
        Position position = new Position("백엔드 개발자", new CodeName("101", "서울"),
                new CodeName("301", "IT"), new CodeName("1", "정규직"),
                new CodeName("2", "IT개발·데이터"), new CodeName("84", "백엔드/서버개발"),
                new ExperienceLevel("1", 0, 0, "신입"), new CodeName("0", "학력무관"));
        return new Job("1", url, 1, new Company(new CompanyDetail(null, "테스트회사")), position,
                "Java,Spring", new CodeName("0", "회사내규"), "100", "100", "100", "200",
                new CodeName("1", "접수마감일"));
    }
}
