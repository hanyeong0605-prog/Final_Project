package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Service
@Transactional
public class LocalDevAuthService {
    private static final String LOGIN_ID = "local-dev";
    private static final String EMAIL = "local-dev@jobpilot.test";
    private static final String NICKNAME = "로컬 개발자";

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public LocalDevAuthService(MemberRepository members, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public AuthResponse issueDevelopmentToken() {
        Member member = members.findByLoginId(LOGIN_ID).orElseGet(this::createDevelopmentMember);
        JwtTokenService.Token token = tokenService.issue(member);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), MemberResponse.from(member));
    }

    private Member createDevelopmentMember() {
        return members.save(new Member(
                LOGIN_ID,
                EMAIL,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                NICKNAME
        ));
    }
}
