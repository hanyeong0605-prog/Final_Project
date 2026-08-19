package com.jobpilot.api.domain.employer.exception;

public class EmployerNotApprovedException extends RuntimeException {
    public EmployerNotApprovedException(String message) { super(message); }
}
