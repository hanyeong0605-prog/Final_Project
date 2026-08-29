package com.jobpilot.api.domain.companyfinance.client;

/** Expected OpenDART status 013: the requested company/year has no statement. */
public class OpenDartNoDataException extends RuntimeException {
    public OpenDartNoDataException() {
        super("OpenDART returned no data for the requested company/year");
    }
}
