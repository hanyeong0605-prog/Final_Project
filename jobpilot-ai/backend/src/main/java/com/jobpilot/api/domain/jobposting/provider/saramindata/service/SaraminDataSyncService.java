package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.jobpilot.api.domain.jobposting.provider.saramindata.config.SaraminDataProperties;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.Job;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataSyncResponse;
import com.jobpilot.api.domain.jobposting.provider.saramindata.service.SaraminDataPersister.SaveResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SaraminDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(SaraminDataSyncService.class);
    private final SaraminDataProperties properties;
    private final SaraminDataClient client;
    private final SaraminDataValidator validator;
    private final SaraminDataCrawler crawler;
    private final SaraminDataNormalizer normalizer;
    private final SaraminDataPersister persister;

    public SaraminDataSyncService(SaraminDataProperties properties, SaraminDataClient client,
                                  SaraminDataValidator validator, SaraminDataCrawler crawler,
                                  SaraminDataNormalizer normalizer, SaraminDataPersister persister) {
        this.properties = properties;
        this.client = client;
        this.validator = validator;
        this.crawler = crawler;
        this.normalizer = normalizer;
        this.persister = persister;
    }

    public SaraminDataSyncResponse sync() {
        properties.requireUsable();
        int fetched = 0, created = 0, updated = 0, skipped = 0, failed = 0;

        for (int page = 0; page < properties.maxPages(); page++) {
            List<Job> jobs;
            try {
                var response = client.fetchPage(page);
                jobs = response.jobs().job() == null ? List.of() : response.jobs().job();
            } catch (Exception exception) {
                failed++;
                log.warn("SaraminDATA page={} 호출 실패: {}", page, exception.getMessage());
                continue;
            }
            if (jobs.isEmpty()) break;
            fetched += jobs.size();

            for (Job job : jobs) {
                var invalid = validator.invalidReason(job);
                if (invalid.isPresent()) {
                    skipped++;
                    log.debug("SaraminDATA 검증 제외 id={}: {}", job == null ? null : job.id(), invalid.get());
                    continue;
                }
                try {
                    var crawlResult = crawler.crawl(job.url());
                    var result = persister.save(normalizer.normalize(job, crawlResult));
                    if (result == SaveResult.CREATED) created++; else updated++;
                } catch (Exception exception) {
                    failed++;
                    log.warn("SaraminDATA 공고 처리 실패 id={}: {}", job.id(), exception.getMessage());
                }
                pauseIfCrawling();
            }
        }
        return new SaraminDataSyncResponse("SARAMIN_DATA", fetched, created, updated, skipped, failed);
    }

    private void pauseIfCrawling() {
        if (!properties.crawlEnabled() || properties.crawlDelayMs() <= 0) return;
        try {
            Thread.sleep(properties.crawlDelayMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
