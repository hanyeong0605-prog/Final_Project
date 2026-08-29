package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.OpenDartCorporation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DartCorporationSyncServiceTest {
    @Test
    void upsertsEveryCorporationReturnedByDart() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(client.downloadCorporations()).thenReturn(List.of(new OpenDartCorporation(
                "00126380", "삼성전자", "Samsung", "005930", "20260829")));
        when(jdbc.batchUpdate(any(String.class), any(org.springframework.jdbc.core.BatchPreparedStatementSetter.class)))
                .thenReturn(new int[] {1});

        int synced = new DartCorporationSyncService(client, jdbc).sync();

        assertEquals(1, synced);
        verify(jdbc).batchUpdate(any(String.class), any(org.springframework.jdbc.core.BatchPreparedStatementSetter.class));
    }

    @Test
    void usesCachedOfficialDirectoryWhenRefreshHasTransientFailure() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM dart_corporations", Integer.class)).thenReturn(118804);

        assertEquals(0, new DartCorporationSyncService(client, jdbc).syncWithCacheFallback());
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void failsWhenRefreshFailsAndThereIsNoCachedDirectory() {
        OpenDartClient client = mock(OpenDartClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(client.downloadCorporations()).thenThrow(new RuntimeException("connection reset"));
        when(jdbc.queryForObject("SELECT COUNT(*) FROM dart_corporations", Integer.class)).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> new DartCorporationSyncService(client, jdbc).syncWithCacheFallback());
    }
}
