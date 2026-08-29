package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.OpenDartFinancialSnapshot;
import com.jobpilot.api.domain.companyfinance.client.OpenDartNoDataException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CompanyFinancialSyncServiceTest {

    @Test
    void storesAnnualStatementOnlyForConfirmedCorporation() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any()))
                .thenReturn(List.of("00126380"));
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        when(client.fetchAnnualConsolidatedStatement("00126380", 2025)).thenReturn(
                new OpenDartFinancialSnapshot(1200L, 100L, 70L, 2000L, 700L, 1300L, 90L));

        int stored = new CompanyFinancialSyncService(client, jdbc).syncConfirmedCompanies(2025, 2025);

        assertEquals(1, stored);
        verify(jdbc).update(anyString(), eq("00126380"), eq(2025), eq("11011"), eq("CFS"),
                eq(1200L), eq(100L), eq(70L), eq(2000L), eq(700L), eq(1300L), eq(90L));
    }

    @Test
    void skipsOnlyNoDataAndPropagatesOperationalDartFailures() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any()))
                .thenReturn(List.of("00126380"));
        when(client.fetchAnnualConsolidatedStatement("00126380", 2024)).thenThrow(new OpenDartNoDataException());
        when(client.fetchAnnualConsolidatedStatement("00126380", 2025)).thenThrow(new IllegalStateException("status=020"));

        var service = new CompanyFinancialSyncService(client, jdbc);
        assertEquals(0, service.syncConfirmedCompanies(2024, 2024));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.syncConfirmedCompanies(2025, 2025));
    }
}
