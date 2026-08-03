package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.LoginRequest;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.auth.dto.SignupRequest;
import com.jobpilot.api.domain.auth.exception.DuplicateMemberException;
import com.jobpilot.api.domain.auth.exception.InvalidCredentialsException;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.member.service.MemberConsentService;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AuthService {
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final EmailVerificationService emailVerificationService;
    private final MemberConsentService memberConsentService;

    public AuthService(MemberRepository members, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       EmailVerificationService emailVerificationService, MemberConsentService memberConsentService) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailVerificationService = emailVerificationService;
        this.memberConsentService = memberConsentService;
    }

    public AuthResponse signup(SignupRequest request) {
        if (members.existsByLoginId(request.loginId())) throw new DuplicateMemberException("이미 사용 중인 로그인 아이디입니다.");
        String email = normalizeEmail(request.email());
        if (members.existsByEmail(email)) throw new DuplicateMemberException("이미 사용 중인 이메일입니다.");
        emailVerificationService.consumeVerifiedEmail(email, request.emailVerificationToken());
        Member member = members.save(new Member(
                request.loginId(), email, passwordEncoder.encode(request.password()), request.nickname()));
        memberConsentService.recordSignupConsents(member, request);
        return response(member);
    }

    public AuthResponse login(LoginRequest request) {
        Member member = members.findByLoginId(request.loginId()).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) throw new InvalidCredentialsException();
        return response(member);
    }

    public MemberResponse me(Long memberId) {
        return MemberResponse.from(members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다.")));
    }

    private AuthResponse response(Member member) {
        JwtTokenService.Token token = tokenService.issue(member);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), MemberResponse.from(member));
    }

    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || !loginId.matches("^[a-zA-Z0-9._-]{6,80}$")) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, 마침표, 밑줄, 하이픈으로 된 6~80자여야 합니다.");
        }
        return !members.existsByLoginId(loginId);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
