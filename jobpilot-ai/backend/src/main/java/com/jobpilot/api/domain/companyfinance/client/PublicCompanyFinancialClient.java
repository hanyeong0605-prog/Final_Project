package com.jobpilot.api.domain.companyfinance.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.parser = parser;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public Optional<PublicCompanyFinancialSnapshot> fetchSummary(String corporateRegistrationNumber, int businessYear) {
        if (serviceKey == null || serviceKey.isBlank() || corporateRegistrationNumber == null || corporateRegistrationNumber.isBlank()) return Optional.empty();
        String query = "serviceKey=" + enc(serviceKey) + "&pageNo=1&numOfRows=10&resultType=json&crno=" + enc(corporateRegistrationNumber) + "&bizYear=" + businessYear;
        String body = client.get().uri(URI.create(baseUrl + "/getSummFinaStat_V2?" + query)).retrieve().body(String.class);
        return parser.parse(body == null ? "" : body);
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
