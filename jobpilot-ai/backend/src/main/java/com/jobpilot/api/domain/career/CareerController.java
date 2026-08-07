package com.jobpilot.api.domain.career;

import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
public class CareerController {

    @Value("${career.api.key}")
    private String apiKey;

    @GetMapping("/questions/{q}")
    public ResponseEntity<String> getTestQuestions(@PathVariable String q) {
        String careerUrl = "https://www.career.go.kr/inspct/openapi/test/questions?apikey=" + apiKey + "&q=" + q;
        RestTemplate restTemplate = new RestTemplate();
        try {
            String responseBody = restTemplate.getForObject(careerUrl, String.class);
            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("커리어넷 API 호출 중 오류 발생: " + e.getMessage());
        }
    }
    @PostMapping("/report")
    public ResponseEntity<String> submitTestReport(@RequestBody Map<String, Object> requestBody) {
        String careerReportUrl = "https://www.career.go.kr/inspct/openapi/test/report";

        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            // 1. 요청 페이로드 구성 및 필수 누락 필드(apikey, school, grade) 강제 주입
            Map<String, Object> payload = new HashMap<>(requestBody);
            payload.put("apikey", apiKey);
            payload.putIfAbsent("school", "일반"); // school이 없으면 기본값 주입
            payload.putIfAbsent("grade", "1");   // grade가 없으면 기본값 주입

            System.out.println("커리어넷 최종 전송 페이로드 (JSON POST): " + payload);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            String responseBody = restTemplate.postForObject(careerReportUrl, entity, String.class);
            System.out.println("커리어넷 결과 응답 성공: " + responseBody);

            return ResponseEntity.ok(responseBody);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("=== 커리어넷 API 4xx 에러 응답 상세 ===");
            System.err.println("상태 코드: " + e.getStatusCode());
            System.err.println("응답 본문: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body("커리어넷 오류: " + e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            System.err.println("=== 커리어넷 API 5xx 서버 에러 상세 ===");
            System.err.println("상태 코드: " + e.getStatusCode());
            System.err.println("응답 본문: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body("커리어넷 서버 오류: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("=== 일반 에러 발생 ===");
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("결과 제출 중 오류 발생: " + e.getMessage());
        }
    }
}