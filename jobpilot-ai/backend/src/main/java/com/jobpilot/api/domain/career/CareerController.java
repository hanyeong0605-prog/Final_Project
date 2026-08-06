package com.jobpilot.api.domain.career;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

    @RestController
    @RequestMapping("/api/tests")
    public class CareerController {

        @Value("${career.api.key}")
        private String apiKey;

        // 예: GET /api/tests/questions/8 요청이 오면 커리어넷 API 호출
        @GetMapping("/questions/{q}")
        public ResponseEntity<String> getTestQuestions(@PathVariable String q) {
            String careerUrl = "https://www.career.go.kr/inspct/openapi/test/questions?apikey=" + apiKey + "&q=" + q;
            System.out.println("요청 URL: " + careerUrl);
            RestTemplate restTemplate = new RestTemplate();
            try {
                // 커리어넷 API 호출 후 결과를 문자열(JSON)로 받아옴
                String responseBody = restTemplate.getForObject(careerUrl, String.class);
                return ResponseEntity.ok(responseBody);


            } catch (Exception e) {
                return ResponseEntity.internalServerError().body("커리어넷 API 호출 중 오류 발생: " + e.getMessage());
            }
        }
    }


