package com.jobpilot.api.domain.jobposting.controller;

import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionRequest;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementExtractionResponse;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementBackfillStartResponse;
import com.jobpilot.api.domain.jobposting.dto.JobRequirementBackfillStatusResponse;
import com.jobpilot.api.domain.jobposting.service.JobRequirementInitialBackfillService;
import com.jobpilot.api.domain.jobposting.service.JobRequirementExtractionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An authenticated, deliberately small-batch endpoint for paid AI preprocessing.
 * It is not called from login or ordinary job-search traffic.
 */
@RestController
@RequestMapping("/api/v1/job-postings/requirement-extractions")
public class JobRequirementExtractionController {
    private final JobRequirementExtractionService extractionService;
    private final JobRequirementInitialBackfillService initialBackfillService;

    public JobRequirementExtractionController(
            JobRequirementExtractionService extractionService,
            JobRequirementInitialBackfillService initialBackfillService
    ) {
        this.extractionService = extractionService;
        this.initialBackfillService = initialBackfillService;
    }

    @PostMapping
    public JobRequirementExtractionResponse extract(
            @Valid @RequestBody(required = false) JobRequirementExtractionRequest request
    ) {
        return extractionService.extract(request);
    }

    @PostMapping("/backfill")
    public JobRequirementBackfillStartResponse startInitialBackfill() {
        return initialBackfillService.start();
    }

    @GetMapping("/backfill")
    public JobRequirementBackfillStatusResponse initialBackfillStatus() {
        return initialBackfillService.status();
    }
}
