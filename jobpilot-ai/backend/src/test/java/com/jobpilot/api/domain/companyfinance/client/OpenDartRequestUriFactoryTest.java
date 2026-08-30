package com.jobpilot.api.domain.companyfinance.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenDartRequestUriFactoryTest {
    @Test
    void buildsCorporationDirectoryRequest() {
        String uri = new OpenDartRequestUriFactory("https://opendart.fss.or.kr").corporationDirectoryUri("secret-not-to-log").toString();

        assertEquals("https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=secret-not-to-log", uri);
    }

    @Test
    void buildsAnnualConsolidatedStatementRequestWithBusinessReportCode() {
        String uri = new OpenDartRequestUriFactory("https://opendart.fss.or.kr").annualStatementUri(
                "secret-not-to-log", "00126380", 2024, "CFS").toString();

        assertEquals("https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json?crtfc_key=secret-not-to-log&corp_code=00126380&bsns_year=2024&reprt_code=11011&fs_div=CFS", uri);
    }

    @Test
    void buildsMultipleCorporationRequestAndEnforcesDartLimit() {
        var factory = new OpenDartRequestUriFactory("https://opendart.fss.or.kr/");

        String uri = factory.multipleAnnualStatementsUri(
                "secret-not-to-log", List.of("00126380", "00164779"), 2025).toString();

        assertEquals("https://opendart.fss.or.kr/api/fnlttMultiAcnt.json?crtfc_key=secret-not-to-log&corp_code=00126380%2C00164779&bsns_year=2025&reprt_code=11011", uri);
        assertThrows(IllegalArgumentException.class,
                () -> factory.multipleAnnualStatementsUri("key", List.of(), 2025));
        assertThrows(IllegalArgumentException.class,
                () -> factory.multipleAnnualStatementsUri("key", java.util.Collections.nCopies(101, "00126380"), 2025));
    }
}
