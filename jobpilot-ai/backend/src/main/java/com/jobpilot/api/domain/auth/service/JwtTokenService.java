package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.member.entity.Member;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration lifetime;

    public JwtTokenService(
            JwtEncoder encoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.lifetime = Duration.ofMinutes(accessTokenMinutes);
    }

    public Token issue(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(member.getId().toString())
                .claim("loginId", member.getLoginId())
                .claim("email", member.getEmail())
                .claim("role", member.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Token(value, lifetime.toSeconds());
    }

    /**
     * 2026-08-19: 기업회원 전용 토큰. "actorType":"EMPLOYER" 클레임으로 일반 회원
     * 토큰과 구분한다 - AuthenticatedMember/AuthenticatedEmployer가 이 클레임으로
     * 서로의 전용 API에 상대 토큰이 쓰이지 못하게 막는다(둘 다 숫자 id를 subject로
     * 쓰기 때문에, 구분 없이는 employer.id와 member.id가 같은 숫자일 때 다른 사람
     * 계정으로 인증되는 사고가 날 수 있다).
     */
    public Token issueForEmployer(EmployerAccount employer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(employer.getId().toString())
                .claim("actorType", "EMPLOYER")
                .claim("loginId", employer.getLoginId())
                .claim("email", employer.getEmail())
                .claim("companyName", employer.getCompanyName())
                .claim("status", employer.getStatus().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Token(value, lifetime.toSeconds());
    }

    public record Token(String value, long expiresInSeconds) {}
}
