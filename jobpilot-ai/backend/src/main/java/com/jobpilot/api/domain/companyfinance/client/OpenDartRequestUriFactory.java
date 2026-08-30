package com.jobpilot.api.domain.companyfinance.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    public URI corporationDirectoryUri(String apiKey) {
        return URI.create(baseUrl + "/api/corpCode.xml?crtfc_key=" + encode(apiKey));
    }

    public URI companyProfileUri(String apiKey, String corpCode) {
        return URI.create(baseUrl + "/api/company.json?crtfc_key=" + encode(apiKey) + "&corp_code=" + encode(corpCode));
    }

    public URI multipleAnnualStatementsUri(String apiKey, List<String> corpCodes, int businessYear) {
        if (corpCodes.isEmpty() || corpCodes.size() > 100) {
            throw new IllegalArgumentException("OpenDART multiple-company request requires 1..100 corporations");
        }
        String query = "crtfc_key=" + encode(apiKey)
                + "&corp_code=" + encode(String.join(",", corpCodes))
                + "&bsns_year=" + businessYear
                + "&reprt_code=11011";
        return URI.create(baseUrl + "/api/fnlttMultiAcnt.json?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
