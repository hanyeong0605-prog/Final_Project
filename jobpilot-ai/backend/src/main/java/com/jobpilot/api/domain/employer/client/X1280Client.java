package com.jobpilot.api.domain.employer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.employer.config.X1280Properties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class X1280Client {
    private final X1280Properties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public X1280Client(X1280Properties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode isRegistered(String userId) { return post("/ap/rest/auth/isAp", Map.of("userId", userId)); }
    public JsonNode registerQr(String userId, String token) { return post("/ap/rest/auth/joinAp", Map.of("userId", userId, "token", token)); }
    public JsonNode result(String userId, String sessionId) { return post("/ap/rest/auth/result", Map.of("userId", userId, "sessionId", sessionId)); }
    public JsonNode cancel(String userId, String sessionId) { return post("/ap/rest/auth/cancel", Map.of("userId", userId, "sessionId", sessionId)); }

    public JsonNode start(String userId, String clientIp, String sessionId, String random) {
        String token = oneTimeToken(userId);
        return post("/ap/rest/auth/getSp", Map.of(
                "userId", userId, "token", token, "clientIp", clientIp,
                "sessionId", sessionId, "random", random, "password", ""));
    }

    private String oneTimeToken(String userId) {
        JsonNode response = post("/ap/rest/auth/getTokenForOneTime", Map.of("userId", userId));
        requireSuccess(response, "One-Time Token 요청 실패");
        String encrypted = response.path("data").path("token").asText("");
        if (encrypted.isBlank()) throw new IllegalStateException("X1280 One-Time Token 응답이 비어 있습니다.");
        return decrypt(encrypted);
    }

    private JsonNode post(String path, Map<String, String> params) {
        if (!properties.enabled()) throw new IllegalStateException("X1280 연동이 비활성화되어 있습니다.");
        try {
            String query = params.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            URI uri = URI.create(properties.baseUrl().toString().replaceAll("/$", "") + path + "?" + query);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("X1280 HTTP 오류: " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("X1280 API 호출이 중단되었습니다.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("X1280 API 호출 실패: " + path, exception);
        }
    }

    private String decrypt(String encrypted) {
        try {
            byte[] key = properties.serverKey().getBytes(StandardCharsets.UTF_8);
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalStateException("X1280 Server Key 길이가 AES 규격과 맞지 않습니다.");
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("X1280 token 복호화 실패", exception);
        }
    }

    private void requireSuccess(JsonNode response, String message) {
        String code = response.path("code").asText("");
        if (!"000".equals(code) && !"000.0".equals(code)) throw new IllegalStateException(message + ": " + code);
    }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
