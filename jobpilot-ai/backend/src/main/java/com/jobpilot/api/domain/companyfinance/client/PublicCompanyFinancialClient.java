package com.jobpilot.api.domain.companyfinance.client;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PublicCompanyFinancialClient {
    private final RestClient client;
    private final PublicCompanyFinancialParser parser;
    private final String serviceKey;
    private final String baseUrl;

    public PublicCompanyFinancialClient(PublicCompanyFinancialParser parser,
                                        @Value("${public-finance.service-key:}") String serviceKey,
                                        @Value("${public-finance.base-url:http://apis.data.go.kr/1160100/service/GetFinaStatInfoService_V2}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        // A bounded recovery must move on from an unavailable provider instead
        // of spending the whole deployment window on one company-year.
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.parser = parser;
        // data.go.kr exposes both an encoded and a decoded variant of the
        // service key. Accept either form, then encode exactly once when the
        // request URI is built below. Otherwise an encoded key (%2F, %3D)
        // becomes double-encoded and the API responds with an empty error body.
        this.serviceKey = normalizeServiceKey(serviceKey);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public Optional<PublicCompanyFinancialSnapshot> fetchSummary(String corporateRegistrationNumber, int businessYear) {
        return fetchSummaryResult(corporateRegistrationNumber, businessYear).snapshot();
    }

    public PublicCompanyFinancialResult fetchSummaryResult(String corporateRegistrationNumber, int businessYear) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return PublicCompanyFinancialResult.failure("CONFIGURATION", "DATA_GO_KR_SERVICE_KEY is not configured.");
        }
        if (corporateRegistrationNumber == null || corporateRegistrationNumber.isBlank()) {
            return PublicCompanyFinancialResult.failure("INVALID_CRNO", "Corporate registration number is blank.");
        }
        String query = "serviceKey=" + enc(serviceKey) + "&pageNo=1&numOfRows=10&resultType=json&crno=" + enc(corporateRegistrationNumber) + "&bizYear=" + businessYear;
        try {
            String body = client.get().uri(URI.create(baseUrl + "/getSummFinaStat_V2?" + query)).retrieve().body(String.class);
            return parser.parseResult(body == null ? "" : body);
        } catch (RestClientException error) {
            // Do not log the exception message: it can include the full request URI and service key.
            return PublicCompanyFinancialResult.failure("HTTP_REQUEST_FAILED", "Public finance API request failed.");
        }
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }

    private static String normalizeServiceKey(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            return URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEncoding) {
            return value.trim();
        }
    }
}
