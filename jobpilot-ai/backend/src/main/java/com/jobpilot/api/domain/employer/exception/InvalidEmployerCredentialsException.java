package com.jobpilot.api.domain.employer.exception;

public class InvalidEmployerCredentialsException extends RuntimeException {
    public InvalidEmployerCredentialsException() { super("아이디 또는 비밀번호가 올바르지 않습니다."); }
}
