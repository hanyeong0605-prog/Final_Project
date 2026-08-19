package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberRole;
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

    // 2026-08-19: 관리자 페이지 접근용 아이디가 없어 테스트가 막힌다는 요청으로 추가 -
    // 기존 local-dev 계정의 role은 건드리지 않고, 완전히 별도의 로컬 전용 관리자 계정을 둔다.
    private static final String ADMIN_LOGIN_ID = "local-dev-admin";
    private static final String ADMIN_EMAIL = "local-dev-admin@jobpilot.test";
    private static final String ADMIN_NICKNAME = "로컬 관리자";

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

    public AuthResponse issueDevelopmentAdminToken() {
        Member member = members.findByLoginId(ADMIN_LOGIN_ID).orElseGet(this::createDevelopmentAdminMember);
        if (member.getRole() != MemberRole.ADMIN) {
            member.changeRole(MemberRole.ADMIN);
            member = members.save(member);
        }
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

    private Member createDevelopmentAdminMember() {
        Member member = new Member(
                ADMIN_LOGIN_ID,
                ADMIN_EMAIL,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                ADMIN_NICKNAME
        );
        member.changeRole(MemberRole.ADMIN);
        return members.save(member);
    }
}
