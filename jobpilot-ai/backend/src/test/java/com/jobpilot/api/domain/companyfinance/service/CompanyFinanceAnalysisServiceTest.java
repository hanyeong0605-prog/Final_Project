package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CompanyFinanceAnalysisServiceTest {

    @Test
    void returnsUnmatchedWithoutAForecastWhenNoDartMatchExists() {
        JobPostingRepository postings = mock(JobPostingRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JobPosting posting = mock(JobPosting.class);
        when(postings.findById(7L)).thenReturn(Optional.of(posting));
        when(posting.getSourceProvider()).thenReturn("WANTED");
        when(posting.getSourceCompanyId()).thenReturn("company-7");
        when(posting.getCompanyName()).thenReturn("비상장 스타트업");
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<CompanyFinanceAnalysisResponse.MatchRow>>any(),
                org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(java.util.List.of());

        CompanyFinanceAnalysisResponse response = new CompanyFinanceAnalysisService(
                postings, jdbc, new CompanyNameNormalizer(), new ObjectMapper()).get(7L);

        assertEquals("UNMATCHED", response.status());
        assertEquals(null, response.forecast());
        assertEquals(0, response.financials().size());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsReadyOnlyWhenAStoredForecastExists() {
        JobPostingRepository postings = mock(JobPostingRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JobPosting posting = mock(JobPosting.class);
        when(postings.findById(9L)).thenReturn(Optional.of(posting));
        when(posting.getSourceProvider()).thenReturn("WANTED");
        when(posting.getSourceCompanyId()).thenReturn("company-9");
        when(posting.getCompanyName()).thenReturn("검증기업");
        var match = List.of(new CompanyFinanceAnalysisResponse.MatchRow("00126380", "CONFIRMED"));
        var years = List.of(
                new CompanyFinanceAnalysisResponse.FinancialYear(2022, 100L, 10L, 8L, 200L, 80L, 120L, 12L, "CFS", "1"),
                new CompanyFinanceAnalysisResponse.FinancialYear(2023, 120L, 12L, 9L, 220L, 85L, 135L, 14L, "CFS", "2"),
                new CompanyFinanceAnalysisResponse.FinancialYear(2024, 140L, 15L, 11L, 250L, 90L, 160L, 17L, "CFS", "3"));
        var forecast = new CompanyFinanceAnalysisResponse.Forecast(2024, "POSITIVE", "HIGH",
                0.72, 0.61, 0.18, "growth-v1", List.of("매출 증가"), "2026-08-29T00:00:00Z");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any(), any()))
                .thenReturn((List) match);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn((List) years, List.of(forecast));

        CompanyFinanceAnalysisResponse response = new CompanyFinanceAnalysisService(
                postings, jdbc, new CompanyNameNormalizer(), new ObjectMapper()).get(9L);

        assertEquals("READY", response.status());
        assertEquals("growth-v1", response.forecast().modelVersion());
        assertEquals(3, response.financials().size());
    }
}
