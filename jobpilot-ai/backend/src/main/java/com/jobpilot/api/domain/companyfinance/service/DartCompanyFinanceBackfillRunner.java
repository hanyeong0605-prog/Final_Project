package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartRequestLimitException;
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
    private final PublicCompanyFinancialSyncService publicFinancialSync;
    private final boolean financialSyncOnStart;
    private final boolean trainingSyncOnStart;
    private final int financialYearsBack;
    private final int trainingCorporationLimit;

    DartCompanyFinanceBackfillRunner(DartCorporationSyncService corporationSync,
                                    CompanyDartBackfillService companyBackfill,
                                    CompanyFinancialSyncService financialSync,
                                    PublicCompanyFinancialSyncService publicFinancialSync,
                                    boolean financialSyncOnStart,
                                    int financialYearsBack) {
        this(corporationSync, companyBackfill, financialSync, publicFinancialSync,
                financialSyncOnStart, false, financialYearsBack, 800);
    }

    public DartCompanyFinanceBackfillRunner(DartCorporationSyncService corporationSync,
                                            CompanyDartBackfillService companyBackfill,
                                            CompanyFinancialSyncService financialSync,
                                            PublicCompanyFinancialSyncService publicFinancialSync,
                                            @Value("${dart.financial-sync-on-start:false}") boolean financialSyncOnStart,
                                            @Value("${dart.training-sync-on-start:false}") boolean trainingSyncOnStart,
                                            @Value("${dart.financial-years-back:7}") int financialYearsBack,
                                            @Value("${dart.training-corporation-limit:800}") int trainingCorporationLimit) {
        this.corporationSync = corporationSync;
        this.companyBackfill = companyBackfill;
        this.financialSync = financialSync;
        this.publicFinancialSync = publicFinancialSync;
        this.financialSyncOnStart = financialSyncOnStart;
        this.trainingSyncOnStart = trainingSyncOnStart;
        if (financialYearsBack < 4) {
            throw new IllegalArgumentException("dart.financial-years-back must be at least 4 for ML labels");
        }
        this.financialYearsBack = financialYearsBack;
        this.trainingCorporationLimit = trainingCorporationLimit;
    }

    @Override
    public void run(ApplicationArguments args) {
        int corporations = corporationSync.syncWithCacheFallback();
        CompanyMatchReport report = companyBackfill.backfillExistingPostings();
        log.info("DART backfill complete: corporations={}, distinctCompanies={}, confirmed={}, candidates={}, unmatched={}",
                corporations, report.distinctCompanies(), report.confirmed(), report.candidates(), report.unmatched());
        if (financialSyncOnStart) {
            int currentYear = java.time.Year.now().getValue();
            try {
                int storedYears = financialSync.syncConfirmedCompanies(currentYear - financialYearsBack, currentYear - 1);
                log.info("DART financial sync complete: storedAnnualStatements={}", storedYears);
            } catch (OpenDartRequestLimitException requestLimit) {
                // DART 기업 정보 동기화 및 재무 데이터 보완
                //채용공고의 기업명을 DART 기업 정보와 연결한 뒤, 최근 연도 재무제표를 조회하여 저장합니다.
                //DART 요청 한도 등으로 일부 데이터가 비어 있는 경우에는 공공 재무정보를 활용해 누락 연도를 보완합니다.
                log.warn("DART financial sync paused because the OpenDART request limit was reached; existing data is preserved and the public-finance fallback will continue for cached registrations.");
            }
            int publicStoredYears = publicFinancialSync.syncMissingAnnualYears(currentYear - financialYearsBack, currentYear - 1);
            log.info("Public finance fallback sync complete: storedAnnualStatements={}", publicStoredYears);
        }
        if (trainingSyncOnStart) {
            int currentYear = java.time.Year.now().getValue();
            try {
                int storedYears = financialSync.syncTrainingUniverse(currentYear - financialYearsBack, currentYear - 1, trainingCorporationLimit);
                log.info("DART training universe sync complete: corporations={}, storedAnnualStatements={}", trainingCorporationLimit, storedYears);
            } catch (OpenDartRequestLimitException requestLimit) {
                log.warn("DART training universe sync paused because the OpenDART request limit was reached; rerun resumes only missing years.");
            }
        }
    }
}
