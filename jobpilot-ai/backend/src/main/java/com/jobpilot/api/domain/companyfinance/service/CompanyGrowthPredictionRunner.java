package com.jobpilot.api.domain.companyfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One-shot, explicit switch used only after a validated model artifact is mounted. */
@Component
@ConditionalOnProperty(prefix="dart", name="prediction-sync-on-start", havingValue="true")
public class CompanyGrowthPredictionRunner implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(CompanyGrowthPredictionRunner.class);
    private final CompanyGrowthPredictionService service;
    public CompanyGrowthPredictionRunner(CompanyGrowthPredictionService service){this.service=service;}
    @Override public void run(ApplicationArguments args){
        log.info("DART validated growth prediction sync complete: storedPredictions={}",service.refreshConfirmedCompanies());
    }
}
