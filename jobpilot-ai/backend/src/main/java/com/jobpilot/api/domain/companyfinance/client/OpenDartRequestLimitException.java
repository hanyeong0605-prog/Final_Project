package com.jobpilot.api.domain.companyfinance.client;

/** OpenDART status 020: the account's request quota is temporarily exhausted. */
public class OpenDartRequestLimitException extends IllegalStateException {
    public OpenDartRequestLimitException() {
        super("OpenDART request limit reached (status=020)");
    }
}
