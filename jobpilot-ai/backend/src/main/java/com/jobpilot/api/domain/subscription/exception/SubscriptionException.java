package com.jobpilot.api.domain.subscription.exception;

// 2026-08-10: 구독 기능 - 이미 구독 중, customerKey 불일치, 토스 API 실패 등 "형식은 맞지만
// 지금 상태에서는 처리할 수 없는" 업무 규칙 위반에 쓴다(ProjectAnalysisException과 같은
// 패턴). GlobalExceptionHandler에서 409로 매핑한다.
public class SubscriptionException extends RuntimeException {
    public SubscriptionException(String message) {
        super(message);
    }
}
