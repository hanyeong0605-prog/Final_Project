package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.LoginRequest;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.auth.dto.SignupRequest;
import com.jobpilot.api.domain.auth.exception.DuplicateMemberException;
import com.jobpilot.api.domain.auth.exception.InvalidCredentialsException;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AuthService {
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(MemberRepository members, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public AuthResponse signup(SignupRequest request) {
        if (members.existsByLoginId(request.loginId())) throw new DuplicateMemberException("이미 사용 중인 로그인 아이디입니다.");
        if (members.existsByEmail(request.email())) throw new DuplicateMemberException("이미 사용 중인 이메일입니다.");
        Member member = members.save(new Member(
                request.loginId(), request.email(), passwordEncoder.encode(request.password()), request.nickname()));
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
}
