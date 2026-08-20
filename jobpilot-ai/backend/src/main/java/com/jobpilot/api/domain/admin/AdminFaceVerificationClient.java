package com.jobpilot.api.domain.admin;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AdminFaceVerificationClient {
    private final RestClient client;
    private final String internalApiKey;

    public AdminFaceVerificationClient(
            @Value("${app.face-verification.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.internal-api-key:}") String internalApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    public Verification verify(String adminId, String imageBase64) {
        try {
            Verification response = client.post().uri("/api/internal/admin/face/verify")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(new VerifyRequest(adminId, imageBase64))
                    .retrieve().body(Verification.class);
            if (response == null) throw new AdminFacePairingException("얼굴 인증 서버가 빈 응답을 반환했습니다.");
            return response;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) {
                throw new AdminFacePairingException("등록되지 않은 관리자입니다. 관리자 기준 사진을 등록해 주세요.");
            }
            if (error.getStatusCode().value() == 401) {
                throw new AdminFacePairingException("얼굴 인증 서비스 권한 설정이 올바르지 않습니다.");
            }
            throw new AdminFacePairingException("얼굴 인증 서버 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException error) {
            throw new AdminFacePairingException("얼굴 인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private record VerifyRequest(String admin_id, String image_base64) {}
    public record Verification(boolean verified, double similarity, String message) {}
}
