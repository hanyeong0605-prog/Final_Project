package com.jobpilot.api.domain.auth.exception;

public class DuplicateMemberException extends RuntimeException {
    public DuplicateMemberException(String message) { super(message); }
}
