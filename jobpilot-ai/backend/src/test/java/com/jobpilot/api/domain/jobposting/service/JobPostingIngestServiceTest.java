package com.jobpilot.api.domain.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.jobposting.dto.JobPostingCrawlBatchRequest;
import com.jobpilot.api.domain.jobposting.dto.JobPostingCrawlItem;
import com.jobpilot.api.domain.jobposting.dto.JobPostingIngestResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

/**
 * 이 환경(JDK21 + Windows)에서 Mockito로 JdbcTemplate을 @Mock 하면
 * "Could not modify all classes" (inline mock maker의 바이트코드 조작/자바 에이전트
 * self-attach 실패)로 죽는다. Mockito 없이, 필요한 메서드만 오버라이드한 가짜
 * JdbcTemplate 서브클래스를 직접 만들어 실제 DB 연결 없이 동작을 검증한다.
 *
 * ingest()는 매번 끝에 closeExpiredPostings()도 호출하기 때문에(마감 공고 정리),
 * 정상 처리 케이스에서는 update()가 "upsert 1번 + 마감 정리 1번" = 2번 불린다.
 */
class JobPostingIngestServiceTest {

    private static class FakeJdbcTemplate extends JdbcTemplate {
        Long sourceIdToReturn; // null이면 "해당 sourceCode를 못 찾음"
        int existingCount; // existsByExternalId가 돌려줄 COUNT(*) 값
        Map<String, String> existingSourceUpdatedAt = Map.of();
        int updateCallCount;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sourceIdToReturn == null) {
                return List.of();
            }
            return (List<T>) List.of(sourceIdToReturn);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T query(String sql, ResultSetExtractor<T> rse, Object... args) {
            return (T) existingSourceUpdatedAt;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(existingCount);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCallCount++;
            return 1;
        }
    }

    private JobPostingCrawlItem validItem() {
        return new JobPostingCrawlItem(
                "ext-1", "백엔드 개발자", "토리든", "https://zighang.com/recruitment/ext-1",
                "36개월", "정규직", "서울", "2026-08-15", false, null, "IT_개발", "설명",
                "2026-07-20T10:00:00"
        );
    }

    @Test
    void throwsWhenSourceCodeIsUnknown() {
        FakeJdbcTemplate fake = new FakeJdbcTemplate();
        fake.sourceIdToReturn = null;
        JobPostingIngestService service = new JobPostingIngestService(fake, new ObjectMapper());

        JobPostingCrawlBatchRequest request =
                new JobPostingCrawlBatchRequest("UNKNOWN", List.of(validItem()));

        assertThatThrownBy(() -> service.ingest(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsNewPostingWhenNotFoundByExternalId() {
        FakeJdbcTemplate fake = new FakeJdbcTemplate();
        fake.sourceIdToReturn = 1L;
        fake.existingCount = 0;
        JobPostingIngestService service = new JobPostingIngestService(fake, new ObjectMapper());

        JobPostingCrawlBatchRequest request =
                new JobPostingCrawlBatchRequest("ZIGHANG", List.of(validItem()));

        JobPostingIngestResult result = service.ingest(request);

        assertThat(result.received()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        // upsert 1번 + closeExpiredPostings 1번
        assertThat(fake.updateCallCount).isEqualTo(2);
    }

    @Test
    void updatesExistingPostingInsteadOfCreatingDuplicate() {
        FakeJdbcTemplate fake = new FakeJdbcTemplate();
        fake.sourceIdToReturn = 1L;
        fake.existingCount = 1;
        JobPostingIngestService service = new JobPostingIngestService(fake, new ObjectMapper());

        JobPostingCrawlBatchRequest request =
                new JobPostingCrawlBatchRequest("ZIGHANG", List.of(validItem()));

        JobPostingIngestResult result = service.ingest(request);

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void skipsItemsMissingRequiredFields() {
        FakeJdbcTemplate fake = new FakeJdbcTemplate();
        fake.sourceIdToReturn = 1L;
        JobPostingIngestService service = new JobPostingIngestService(fake, new ObjectMapper());

        JobPostingCrawlItem invalid = new JobPostingCrawlItem(
                null, null, "토리든", null, null, null, null, null, false, null, null, null, null
        );
        JobPostingCrawlBatchRequest request =
                new JobPostingCrawlBatchRequest("ZIGHANG", List.of(invalid));

        JobPostingIngestResult result = service.ingest(request);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        // upsert는 안 불렸지만 closeExpiredPostings 1번은 항상 돈다.
        assertThat(fake.updateCallCount).isEqualTo(1);
    }

    @Test
    void findExistingSourceUpdatedAtReturnsStoredMapForKnownSource() {
        FakeJdbcTemplate fake = new FakeJdbcTemplate();
        fake.sourceIdToReturn = 1L;
        fake.existingSourceUpdatedAt = Map.of("ext-1", "2026-07-20T10:00:00");
        JobPostingIngestService service = new JobPostingIngestService(fake, new ObjectMapper());

        Map<String, String> result = service.findExistingSourceUpdatedAt("ZIGHANG");

        assertThat(result).containsEntry("ext-1", "2026-07-20T10:00:00");
    }
}
