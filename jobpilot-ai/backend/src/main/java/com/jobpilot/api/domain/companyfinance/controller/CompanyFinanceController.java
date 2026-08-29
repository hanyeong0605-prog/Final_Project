package com.jobpilot.api.domain.companyfinance.controller;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.companyfinance.service.CompanyFinanceAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-postings/{jobPostingId}/company-finance")
public class CompanyFinanceController {
    private final CompanyFinanceAnalysisService analysisService;

    public CompanyFinanceController(CompanyFinanceAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping
    public CompanyFinanceAnalysisResponse get(@PathVariable long jobPostingId) {
        return analysisService.get(jobPostingId);
    }
}
