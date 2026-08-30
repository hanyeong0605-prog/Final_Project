package com.jobpilot.api.domain.companyfinance.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenDartClient {
    private final RestClient restClient;
    private final OpenDartFinancialStatementParser parser;
    private final OpenDartCorporationZipParser corporationZipParser;
    private final OpenDartRequestUriFactory uriFactory;
    private final String apiKey;

    public OpenDartClient(
            OpenDartFinancialStatementParser parser,
            OpenDartCorporationZipParser corporationZipParser,
            @Value("${dart.api-key:}") String apiKey,
            @Value("${dart.base-url:https://opendart.fss.or.kr}") String baseUrl
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.parser = parser;
        this.corporationZipParser = corporationZipParser;
        this.uriFactory = new OpenDartRequestUriFactory(baseUrl);
        this.apiKey = apiKey;
    }

    public List<OpenDartCorporation> downloadCorporations() {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("DART API key is not configured");
        byte[] zip = restClient.get().uri(uriFactory.corporationDirectoryUri(apiKey)).retrieve().body(byte[].class);
        try {
            return corporationZipParser.parse(zip == null ? new byte[0] : zip);
        } catch (Exception error) {
            throw new IllegalStateException("DART corporation directory could not be parsed", error);
        }
    }

    public OpenDartFinancialSnapshot fetchAnnualConsolidatedStatement(String corpCode, int businessYear) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("DART API key is not configured");
        String body = restClient.get().uri(uriFactory.annualStatementUri(apiKey, corpCode, businessYear, "CFS"))
                .retrieve().body(String.class);
        try {
            return parser.parse(body == null ? "" : body);
        } catch (OpenDartNoDataException noData) {
            throw noData;
        } catch (Exception error) {
            throw new IllegalStateException("DART financial statement response could not be parsed", error);
        }
    }

    public Map<String, OpenDartFinancialSnapshot> fetchMultipleAnnualStatements(List<String> corpCodes,
                                                                                 int businessYear) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("DART API key is not configured");
        String body = restClient.get().uri(uriFactory.multipleAnnualStatementsUri(apiKey, corpCodes, businessYear))
                .retrieve().body(String.class);
        try {
            return parser.parseMultiple(body == null ? "" : body);
        } catch (OpenDartNoDataException noData) {
            throw noData;
        } catch (Exception error) {
            throw new IllegalStateException("DART multiple-company statement response could not be parsed", error);
        }
    }
}
