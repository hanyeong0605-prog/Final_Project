package com.jobpilot.api.domain.companyfinance.service;

/** DART exposes both Korean and English legal names; either can be the crawler's company label. */
public record DartCorporationCandidate(String corpCode, String corpName, String corpEngName) {
    public DartCorporationCandidate(String corpCode, String corpName) {
        this(corpCode, corpName, "");
    }
}
