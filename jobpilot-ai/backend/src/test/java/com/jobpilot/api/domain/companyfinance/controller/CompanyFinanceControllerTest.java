package com.jobpilot.api.domain.companyfinance.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.companyfinance.service.CompanyFinanceAnalysisService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompanyFinanceControllerTest {

    @Test
    void returnsCachedAnalysisForPosting() throws Exception {
        CompanyFinanceAnalysisService service = mock(CompanyFinanceAnalysisService.class);
        when(service.get(7L)).thenReturn(new CompanyFinanceAnalysisResponse(
                "FINANCIALS_ONLY", "재무제표 분석은 준비되었습니다.", "00126380", List.of(), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanyFinanceController(service)).build();

        mvc.perform(get("/api/v1/job-postings/7/company-finance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINANCIALS_ONLY"))
                .andExpect(jsonPath("$.corpCode").value("00126380"));
    }
}
