package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaraminDataNormalizerTest {
    private final SaraminDataNormalizer normalizer = new SaraminDataNormalizer(new ObjectMapper());

    @Test
    void normalizesApiAndCrawlDataIntoCommonPostingShape() {
        Position position = new Position("백엔드 개발자", new CodeName("101", "서울"),
                new CodeName("301", "IT"), new CodeName("1", "정규직"),
                new CodeName("2", "IT개발·데이터"), new CodeName("84", "백엔드/서버개발"),
                new ExperienceLevel("1", 0, 0, "신입"), new CodeName("0", "학력무관"));
        Job job = new Job("123", "http://www.saramin.co.kr/jobs/123", 1,
                new Company(new CompanyDetail(null, "테스트회사")), position, "Java,Spring",
                new CodeName("0", "회사내규"), "1700000000", "1700000100", "1700000000",
                "1800000000", new CodeName("2", "채용시"));

        var result = normalizer.normalize(job,
                new SaraminDataCrawler.CrawlResult("상세 업무 내용", List.of("Spring 경험자 우대"), "SUCCESS"));

        assertThat(result.externalJobId()).isEqualTo("123");
        assertThat(result.sourceUrl()).startsWith("https://");
        assertThat(result.description()).isEqualTo("상세 업무 내용");
        assertThat(result.rollingDeadline()).isTrue();
        assertThat(result.requirements()).extracting(item -> item.type())
                .contains("REQUIRED", "PREFERRED");
        assertThat(result.rawPayload().get("id").asText()).isEqualTo("123");
    }
}
