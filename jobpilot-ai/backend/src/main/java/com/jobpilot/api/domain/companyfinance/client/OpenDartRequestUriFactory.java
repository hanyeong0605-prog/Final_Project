package com.jobpilot.api.domain.companyfinance.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OpenDartRequestUriFactory {
    private final String baseUrl;

    public OpenDartRequestUriFactory(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public URI annualStatementUri(String apiKey, String corpCode, int businessYear, String fsDiv) {
        String query = "crtfc_key=" + encode(apiKey)
                + "&corp_code=" + encode(corpCode)
                + "&bsns_year=" + businessYear
                + "&reprt_code=11011"
                + "&fs_div=" + encode(fsDiv);
        return URI.create(baseUrl + "/api/fnlttSinglAcntAll.json?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
