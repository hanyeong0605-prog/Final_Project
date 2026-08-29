package com.jobpilot.api.domain.companyfinance.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OpenDartRequestUriFactoryTest {
    @Test
    void buildsAnnualConsolidatedStatementRequestWithBusinessReportCode() {
        String uri = new OpenDartRequestUriFactory("https://opendart.fss.or.kr").annualStatementUri(
                "secret-not-to-log", "00126380", 2024, "CFS").toString();

        assertEquals("https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json?crtfc_key=secret-not-to-log&corp_code=00126380&bsns_year=2024&reprt_code=11011&fs_div=CFS", uri);
    }
}
