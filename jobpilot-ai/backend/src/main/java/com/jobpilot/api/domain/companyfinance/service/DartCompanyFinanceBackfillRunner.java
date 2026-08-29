package com.jobpilot.api.domain.companyfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs only when DART_BACKFILL_ON_START=true is explicitly supplied to the backend container. */
@Component
@ConditionalOnProperty(prefix = "dart", name = "backfill-on-start", havingValue = "true")
public class DartCompanyFinanceBackfillRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DartCompanyFinanceBackfillRunner.class);
    private final DartCorporationSyncService corporationSync;
    private final CompanyDartBackfillService companyBackfill;
    private final CompanyFinancialSyncService financialSync;
    private final boolean financialSyncOnStart;

    public DartCompanyFinanceBackfillRunner(DartCorporationSyncService corporationSync,
                                            CompanyDartBackfillService companyBackfill,
                                            CompanyFinancialSyncService financialSync,
                                            @Value("${dart.financial-sync-on-start:false}") boolean financialSyncOnStart) {
        this.corporationSync = corporationSync;
        this.companyBackfill = companyBackfill;
        this.financialSync = financialSync;
        this.financialSyncOnStart = financialSyncOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        int corporations = corporationSync.sync();
        CompanyMatchReport report = companyBackfill.backfillExistingPostings();
        log.info("DART backfill complete: corporations={}, distinctCompanies={}, confirmed={}, candidates={}, unmatched={}",
                corporations, report.distinctCompanies(), report.confirmed(), report.candidates(), report.unmatched());
        if (financialSyncOnStart) {
            int currentYear = java.time.Year.now().getValue();
            int storedYears = financialSync.syncConfirmedCompanies(currentYear - 3, currentYear - 1);
            log.info("DART financial sync complete: storedAnnualStatements={}", storedYears);
        }
    }
}
