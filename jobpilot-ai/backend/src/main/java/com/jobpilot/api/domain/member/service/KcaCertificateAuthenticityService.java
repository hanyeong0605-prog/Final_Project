package com.jobpilot.api.domain.member.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국방송통신전파진흥원(KCA) 국가기술자격증(개인) 진위여부(API).
 *
 * 2026-08-11: data.go.kr 마이페이지에서 확인한 요청변수는 serviceKey + no(자격증 발급번호)
 * 딱 두 개뿐이다(이름/생년월일 없이 발급번호만으로 조회). Q-Net(한국산업인력공단) API들과
 * 서비스 제공기관/엔드포인트가 아예 다르지만(apis.data.go.kr, JSON), 같은 계정의 일반
 * 인증키(qnet.api.key)를 공유해서 쓴다(세 API 모두 같은 키로 승인돼 있음, 마이페이지 확인).
 *
 * 주의: 이 API는 무선설비/통신설비/전파전자/정보통신 분야(KCA 소관) 국가기술자격증만
 * 커버한다 - 정보처리기사 같은 다른 분야 국가기술자격은 이 API로 진위확인이 안 된다.
 * 엔드포인트(getCqCertificateCheck)는 data.go.kr "미리보기" 테스트 호출 URL로 직접
 * 확인함(2026-08-11). 정상 응답이 아니면 바로 IllegalStateException으로 fail-closed -
 * 자격 정보가 걸린 검증이라 애매하면 통과시키지 않는다.
 */
@Service
public class KcaCertificateAuthenticityService {
    private static final String CHECK_URL = "https://apis.data.go.kr/B552729/kcaApiService_cq2/getCqCertificateCheck";

    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KcaCertificateAuthenticityService(@Value("${qnet.api.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean checkAuthenticity(String certificateNumber) {
        String no = requireNumber(certificateNumber);
        if (apiKey.isBlank()) throw new IllegalStateException("자격증 진위확인 API 키가 설정되지 않았습니다.");
        URI uri = UriComponentsBuilder.fromUriString(CHECK_URL)
                .queryParam("no", no)
                // serviceKey is an opaque credential: never decode or transform it before sending.
                .queryParam("serviceKey", apiKey)
                .build().encode().toUri();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; JobPilot/1.0)");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) throw new IllegalStateException("진위확인 응답이 비어 있습니다.");
            JsonNode items = objectMapper.readTree(body).path("response").path("body").path("items");
            if (items.isMissingNode() || !items.hasNonNull("result")) {
                throw new IllegalStateException("진위확인 응답 형식이 예상과 다릅니다.");
            }
            return items.path("result").asBoolean(false);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("자격증 진위확인에 실패했습니다.");
        }
    }

    private String requireNumber(String no) {
        if (no == null || no.isBlank()) throw new IllegalArgumentException("자격증 발급번호가 필요합니다.");
        return no.trim();
    }
}
