package com.jobpilot.api.domain.employer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.auth.service.JwtTokenService;
import com.jobpilot.api.domain.employer.client.X1280Client;
import com.jobpilot.api.domain.employer.dto.EmployerAuthResponse;
import com.jobpilot.api.domain.employer.dto.EmployerResponse;
import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.exception.EmployerNotApprovedException;
import com.jobpilot.api.domain.employer.exception.InvalidEmployerCredentialsException;
import com.jobpilot.api.domain.employer.repository.EmployerAccountRepository;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class EmployerPasswordlessService {
    private final EmployerAccountRepository employers;
    private final PasswordEncoder passwordEncoder;
    private final X1280Client client;
    private final JwtTokenService tokens;

    public EmployerPasswordlessService(EmployerAccountRepository employers, PasswordEncoder passwordEncoder,
                                       X1280Client client, JwtTokenService tokens) {
        this.employers = employers; this.passwordEncoder = passwordEncoder; this.client = client; this.tokens = tokens;
    }

    public Map<String, Object> enrollment(String loginId, String password) {
        EmployerAccount employer = credential(loginId, password);
        requireApproved(employer);
        JsonNode check = client.isRegistered(employer.getPasswordlessUserId());
        if (check.path("data").path("exist").asBoolean(false)) {
            employer.activatePasswordless();
            return Map.of("registered", true, "status", employer.getPasswordlessStatus().name());
        }
        JsonNode response = client.registerQr(employer.getPasswordlessUserId(), UUID.randomUUID().toString());
        requireSuccess(response, "X1280 QR 등록 요청 실패");
        return Map.of("registered", false, "status", employer.getPasswordlessStatus().name(), "data", response.path("data"));
    }

    public Map<String, Object> enrollmentStatus(String loginId, String password) {
        EmployerAccount employer = credential(loginId, password);
        requireApproved(employer);
        boolean registered = client.isRegistered(employer.getPasswordlessUserId()).path("data").path("exist").asBoolean(false);
        if (registered) employer.activatePasswordless();
        return Map.of("registered", registered, "status", employer.getPasswordlessStatus().name());
    }

    public Map<String, Object> start(String loginId, String clientIp) {
        EmployerAccount employer = find(loginId);
        requireApproved(employer);
        boolean registered = client.isRegistered(employer.getPasswordlessUserId()).path("data").path("exist").asBoolean(false);
        if (!registered) throw new IllegalArgumentException("Passwordless 기기 등록이 필요합니다.");
        if (employer.getPasswordlessStatus().name().equals("ENROLL_REQUIRED")) employer.activatePasswordless();
        String sessionId = System.currentTimeMillis() + "-" + UUID.randomUUID();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode response = client.start(employer.getPasswordlessUserId(), clientIp, sessionId, random);
        requireSuccess(response, "X1280 인증 요청 실패");
        return Map.of("result", "OK", "sessionId", sessionId, "data", response.path("data"));
    }

    public Object result(String loginId, String sessionId) {
        EmployerAccount employer = find(loginId);
        JsonNode response = client.result(employer.getPasswordlessUserId(), sessionId);
        String code = response.path("code").asText("");
        String auth = response.path("data").path("auth").asText("");
        if (("000".equals(code) || "000.0".equals(code)) && "Y".equals(auth)) {
            employer.recordPasswordlessVerification();
            JwtTokenService.Token token = tokens.issueForEmployer(employer);
            return new EmployerAuthResponse(token.value(), "Bearer", token.expiresInSeconds(), EmployerResponse.from(employer));
        }
        return Map.of("result", "WAIT", "data", response.path("data"));
    }

    public Map<String, Object> cancel(String loginId, String sessionId) {
        EmployerAccount employer = find(loginId);
        client.cancel(employer.getPasswordlessUserId(), sessionId);
        return Map.of("result", "OK");
    }

    private EmployerAccount credential(String loginId, String password) {
        EmployerAccount employer = find(loginId);
        if (!passwordEncoder.matches(password, employer.getPasswordHash())) throw new InvalidEmployerCredentialsException();
        return employer;
    }
    private EmployerAccount find(String loginId) { return employers.findByLoginId(loginId).orElseThrow(InvalidEmployerCredentialsException::new); }
    private void requireApproved(EmployerAccount employer) {
        if (!employer.isApproved()) {
            throw new EmployerNotApprovedException("관리자 승인이 완료된 기업회원만 Passwordless를 사용할 수 있습니다.");
        }
    }
    private void requireSuccess(JsonNode response, String message) {
        String code = response.path("code").asText("");
        if (!"000".equals(code) && !"000.0".equals(code)) throw new IllegalStateException(message + ": " + code);
    }
}
