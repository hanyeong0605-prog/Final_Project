package com.jobpilot.api.domain.companyfinance.service;

import java.time.Year;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DartCompanyFinanceBackfillRunnerTest {
    @Test
    void collectsSevenCompletedYearsForTrainingAndHoldoutLabels() {
        var corporations = mock(DartCorporationSyncService.class);
        var matching = mock(CompanyDartBackfillService.class);
        var financials = mock(CompanyFinancialSyncService.class);
        var publicFinancials = mock(PublicCompanyFinancialSyncService.class);
        when(corporations.sync()).thenReturn(0);
        when(matching.backfillExistingPostings()).thenReturn(new CompanyMatchReport(0, 0, 0, 0));

        new DartCompanyFinanceBackfillRunner(corporations, matching, financials, publicFinancials, true, 7).run(null);

        int currentYear = Year.now().getValue();
        verify(financials).syncConfirmedCompanies(currentYear - 7, currentYear - 1);
        verify(publicFinancials).syncMissingAnnualYears(currentYear - 7, currentYear - 1);
    }

    @Test
    void refusesRangeThatCannotProduceAnyLabeledRow() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DartCompanyFinanceBackfillRunner(
                mock(DartCorporationSyncService.class), mock(CompanyDartBackfillService.class),
                mock(CompanyFinancialSyncService.class), mock(PublicCompanyFinancialSyncService.class), true, 3));
    }
}
