package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import java.util.Optional;
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
                postings, jdbc, new CompanyNameNormalizer()).get(7L);

        assertEquals("UNMATCHED", response.status());
        assertEquals(null, response.forecast());
        assertEquals(0, response.financials().size());
    }
}
