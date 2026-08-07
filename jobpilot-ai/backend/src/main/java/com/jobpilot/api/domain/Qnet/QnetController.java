package com.jobpilot.api.domain.Qnet;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/qnet")
public class QnetController {

    @GetMapping("/qualifications")
    public ResponseEntity<String> getQualifications(@RequestParam("serviceKey") String serviceKey) {
        try {
            // 1. 공공데이터포털 요청 주소
            String baseUrl = "http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC/getList";

            // 2. UriComponentsBuilder를 사용해 안전하게 URI 객체 생성 (인증키 중복 인코딩 방지)
            URI targetUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("serviceKey", serviceKey)
                    .build(false)
                    .toUri();

            System.out.println("최종 요청 URI: " + targetUrl);

            // 3. RestTemplate을 이용한 간편한 GET 요청
            RestTemplate restTemplate = new RestTemplate();
            String responseBody = restTemplate.getForObject(targetUrl, String.class);

            System.out.println("Q-Net 응답 데이터 성공");
            return ResponseEntity.ok(responseBody);

        } catch (HttpStatusCodeException e) {
            // 외부 공공데이터 서버가 4xx, 5xx 에러를 응답한 경우
            System.err.println("외부 API 에러 코드: " + e.getStatusCode());
            System.err.println("외부 API 에러 바디: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());

        } catch (Exception e) {
            // 기타 내부 에러
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}


/*  데이터가 안 나옴 / 페이지 라우팅해서 빈껍데기는 나오는데 내용물이 없음. */