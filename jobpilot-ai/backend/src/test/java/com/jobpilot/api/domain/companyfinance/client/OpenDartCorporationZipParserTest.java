package com.jobpilot.api.domain.companyfinance.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class OpenDartCorporationZipParserTest {
    @Test
    void readsCorporationCodesAndNamesFromDartZip() throws Exception {
        byte[] zip = zip("CORPCODE.xml", """
                <result><list>
                <corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><corp_eng_name>Samsung</corp_eng_name><stock_code>005930</stock_code><modify_date>20260829</modify_date>
                </list></result>
                """);

        var corporations = new OpenDartCorporationZipParser().parse(zip);

        assertEquals(1, corporations.size());
        assertEquals("00126380", corporations.getFirst().corpCode());
        assertEquals("삼성전자", corporations.getFirst().corpName());
        assertEquals("005930", corporations.getFirst().stockCode());
    }

    private byte[] zip(String name, String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(name));
            output.write(xml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }
}
