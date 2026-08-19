package com.jobpilot.api.domain.employer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국세청 "사업자등록정보 진위확인 및 상태조회" 오픈API(공공데이터포털, data.go.kr) 중
 * 진위확인(validate) 서비스를 호출한다.
 * https://www.data.go.kr/data/15081808/openapi.do
 *
 * 2026-08-19: 기업회원 가입 시점에 사업자번호+개업일자+대표자명이 국세청 데이터와
 * 일치하는지 즉시 확인해서 관리자 페이지에 "인증완료/확인필요"로 보여주기 위해 추가.
 *
 * fail-safe 정책: 서비스 키가 없거나(NTS_API_KEY 미설정) API 호출이 실패해도 예외를
 * 던지지 않고 verified=false(확인 필요)로 반환한다 - 이 결과는 최종 승인 여부를
 * 결정하지 않고 어디까지나 "관리자가 참고하는 표시"일 뿐이며, 실제 계정 활성화는
 * 관리자가 수동으로 승인 버튼을 눌러야만 이루어진다(EmployerAccount.approve 참고).
 * 그래서 진위확인 API 자체가 불안정해도 가입 절차 전체가 막히지 않는다.
 */
@Component
public class NtsBusinessVerificationClient {
    private static final Logger log = LoggerFactory.getLogger(NtsBusinessVerificationClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public NtsBusinessVerificationClient(@Value("${nts.api.key:}") String serviceKey, ObjectMapper objectMapper) {
        this.serviceKey = serviceKey;
        this.objectMapper = objectMapper;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.odcloud.kr")
                .requestFactory(requestFactory)
                .build();
    }

    public Result verify(String businessRegistrationNumber, String openingDate, String representativeName) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.info("NTS_API_KEY가 설정되지 않아 사업자 진위확인을 건너뜁니다 - '확인 필요' 상태로 둡니다.");
            return Result.unverified("{\"skipped\":\"nts.api.key not configured\"}");
        }
        try {
            Map<String, Object> business = new LinkedHashMap<>();
            business.put("b_no", businessRegistrationNumber);
            business.put("start_dt", openingDate);
            business.put("p_nm", representativeName);
            business.put("p_nm2", "");
            business.put("b_nm", "");
            business.put("corp_no", "");
            business.put("b_sector", "");
            business.put("b_type", "");
            Map<String, Object> requestBody = Map.of("businesses", List.of(business));

            String rawResponse = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/api/nts-businessman/v1/validate")
                            .queryParam("serviceKey", serviceKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            boolean verified = extractValid(rawResponse);
            return new Result(verified, rawResponse);
        } catch (Exception e) {
            log.warn("국세청 사업자등록정보 진위확인 API 호출 실패 - '확인 필요' 상태로 둡니다: {}", e.getMessage());
            return Result.unverified("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private boolean extractValid(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                // valid: "01" = 일치(진위 확인됨), "02" = 확인 불가/불일치.
                String valid = data.get(0).path("valid").asText("");
                return "01".equals(valid);
            }
        } catch (Exception e) {
            log.warn("국세청 진위확인 응답 파싱 실패: {}", e.getMessage());
        }
        return false;
    }

    public record Result(boolean verified, String rawResponse) {
        static Result unverified(String rawResponse) { return new Result(false, rawResponse); }
    }
}
