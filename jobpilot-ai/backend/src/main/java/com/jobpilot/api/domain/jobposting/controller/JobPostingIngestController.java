package com.jobpilot.api.domain.jobposting.controller;

import com.jobpilot.api.domain.jobposting.dto.JobPostingCrawlBatchRequest;
import com.jobpilot.api.domain.jobposting.dto.JobPostingIngestResult;
import com.jobpilot.api.domain.jobposting.service.JobPostingIngestService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ai-server(파이썬 크롤러)가 크롤링한 공고를 넘기는 진입점. */
@RestController
@RequestMapping("/api/v1/job-postings")
public class JobPostingIngestController {
    private final JobPostingIngestService jobPostingIngestService;

    public JobPostingIngestController(JobPostingIngestService jobPostingIngestService) {
        this.jobPostingIngestService = jobPostingIngestService;
    }

    @PostMapping("/ingest")
    public JobPostingIngestResult ingest(@RequestBody JobPostingCrawlBatchRequest request) {
        return jobPostingIngestService.ingest(request);
    }

    /**
     * 크롤러가 크롤링 전에 호출해서 {externalId: source_updated_at} 맵을 받아간다.
     * sitemap의 lastmod가 이 값보다 안 새로운 공고는 상세 페이지를 다시 안 열어봐도 된다.
     */
    @GetMapping("/existing")
    public Map<String, String> existing(@RequestParam String sourceCode) {
        return jobPostingIngestService.findExistingSourceUpdatedAt(sourceCode);
    }
}
