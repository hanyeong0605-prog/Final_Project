package com.jobpilot.api.domain.Check;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/checks")
public class CheckController {

    @Value("${barun.api.key}")
    private String apiKey;

    @PostMapping("/correct")
    public ResponseEntity<String> getCheck(@RequestBody Map<String, String> requestBody) {
        String textToCorrect = requestBody.get("q"); // 프론트에서 보낸 텍스트

        String checkUrl = "https://api.bareun.ai/bareun.RevisionService/CorrectError";
        System.out.println("요청 URL: " + checkUrl);

        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정 (API 키 및 컨텐츠 타입 지정)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey); // 바른 API 인증 방식에 따른 헤더 설정

        // 2. 바른 API 명세에 맞는 Request Body 구성
        // 문서 참고: https://bareun.ai/docs/howtouse/api-correct/
        Map<String, Object> document = new HashMap<>();
        document.put("content", textToCorrect);
        document.put("language", "ko-KR");

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("document", document);
        bodyMap.put("encoding_type", "UTF32");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

        try {
            // 3. POST 요청 전송
            ResponseEntity<String> response = restTemplate.exchange(
                    checkUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("API 호출 중 오류 발생: " + e.getMessage());
        }
    }
}
