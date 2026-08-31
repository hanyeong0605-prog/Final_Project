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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

class CompanyFinancialSyncServiceTest {

    @Test
    void storesAnnualStatementOnlyForConfirmedListedCorporation() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockListedCorporation(jdbc);
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        when(client.fetchMultipleAnnualStatements(List.of("00126380"), 2025)).thenReturn(Map.of(
                "005930", new OpenDartFinancialSnapshot(1200L, 100L, 70L, 2000L, 700L, 1300L, null)));

        int stored = new CompanyFinancialSyncService(client, jdbc).syncConfirmedCompanies(2025, 2025);

        assertEquals(1, stored);
        verify(jdbc).update(anyString(), eq("00126380"), eq(2025), eq("11011"), eq("CFS"),
                eq(1200L), eq(100L), eq(70L), eq(2000L), eq(700L), eq(1300L), eq(null));
    }

    @Test
    void skipsOnlyNoDataAndPropagatesOperationalDartFailures() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockListedCorporation(jdbc);
        when(client.fetchMultipleAnnualStatements(List.of("00126380"), 2024)).thenThrow(new OpenDartNoDataException());
        when(client.fetchMultipleAnnualStatements(List.of("00126380"), 2025)).thenThrow(new IllegalStateException("status=020"));

        var service = new CompanyFinancialSyncService(client, jdbc);
        assertEquals(0, service.syncConfirmedCompanies(2024, 2024));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.syncConfirmedCompanies(2025, 2025));
    }

    @Test
    void retriesTransientNetworkFailureAndKeepsBatchProgress() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockListedCorporation(jdbc);
        when(client.fetchMultipleAnnualStatements(List.of("00126380"), 2025))
                .thenThrow(new ResourceAccessException("reset"))
                .thenThrow(new ResourceAccessException("reset"))
                .thenReturn(Map.of("005930",
                        new OpenDartFinancialSnapshot(1200L, 100L, 70L, 2000L, 700L, 1300L, null)));

        assertEquals(1, new CompanyFinancialSyncService(client, jdbc).syncConfirmedCompanies(2025, 2025));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(3))
                .fetchMultipleAnnualStatements(List.of("00126380"), 2025);
    }

    @Test
    void skipsDartRequestForAnAnnualStatementAlreadyStoredFromDart() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        mockListedCorporation(jdbc);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(2025))).thenReturn(List.of("00126380"));

        assertEquals(0, new CompanyFinancialSyncService(client, jdbc).syncConfirmedCompanies(2025, 2025));

        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void storesConfirmedUnlistedCorporationWithSingleCompanyDartEndpoint() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("00999999"));
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        when(client.fetchAnnualConsolidatedStatement("00999999", 2025)).thenReturn(
                new OpenDartFinancialSnapshot(900L, 80L, 55L, 1500L, 400L, 1100L, null));

        int stored = new CompanyFinancialSyncService(client, jdbc).syncConfirmedCompanies(2025, 2025);

        assertEquals(1, stored);
        verify(jdbc).update(anyString(), eq("00999999"), eq(2025), eq("11011"), eq("CFS"),
                eq(900L), eq(80L), eq(55L), eq(1500L), eq(400L), eq(1100L), eq(null));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockListedCorporation(JdbcTemplate jdbc) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(invocation -> {
                    org.springframework.jdbc.core.RowMapper mapper = invocation.getArgument(1);
                    java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
                    when(resultSet.getString(1)).thenReturn("00126380");
                    when(resultSet.getString(2)).thenReturn("005930");
                return List.of(mapper.mapRow(resultSet, 0));
        });
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
    }
}
