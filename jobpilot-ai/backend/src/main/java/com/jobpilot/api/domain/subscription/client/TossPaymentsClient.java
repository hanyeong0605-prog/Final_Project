package com.jobpilot.api.domain.subscription.client;

import com.jobpilot.api.domain.subscription.exception.SubscriptionException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 2026-08-10: 구독 기능 - 처음엔 토스 자동결제(빌링) API로 만들었는데, 그 API는 문서
 * 테스트 키만으로는 안 되고 "추가 계약 후 테스트/라이브 환경에서 사용 가능"이라 테스트가
 * 막혔다("테스트 결제만 되면 된다"는 피드백). 그래서 계약 없이 바로 되는 일반 결제창(v1)
 * API로 바꿨다 - https://docs.tosspayments.com/guides/payment/integration 가이드 그대로.
 *
 * 대신 카드를 저장해두고 매달 자동으로 다시 청구하는 게 불가능해졌다 - SubscriptionService
 * docstring 참고("진짜 자동결제"가 아니라 "달마다 사용자가 결제창에서 다시 결제"하는 방식).
 *
 * fail-open을 쓰지 않는다 - 실패 시 무조건 SubscriptionException을 던져서 구독이
 * 활성화/연장되지 않게 한다(의심스러우면 지급 안 함이 안전한 기본값).
 */
@Component
public class TossPaymentsClient {
    private final RestClient restClient;
    private final String authorizationHeader;

    public TossPaymentsClient(@Value("${toss.secret-key:}") String secretKey) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .requestFactory(requestFactory)
                .build();
        this.authorizationHeader = "Basic "
                + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    /** 성공 시 토스 Payment 객체(JSON을 Map으로)를 그대로 돌려준다. 실패 시 SubscriptionException. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> confirmPayment(String paymentKey, String orderId, int amount) {
        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Authorization", authorizationHeader)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new SubscriptionException("토스 결제 승인에 실패했습니다: " + extractMessage(e));
        } catch (Exception e) {
            throw new SubscriptionException("토스 결제 승인 중 통신 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessage(RestClientResponseException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(Map.class);
            Object message = body == null ? null : body.get("message");
            return message != null ? message.toString() : e.getMessage();
        } catch (Exception parseFailure) {
            return e.getMessage();
        }
    }
}
