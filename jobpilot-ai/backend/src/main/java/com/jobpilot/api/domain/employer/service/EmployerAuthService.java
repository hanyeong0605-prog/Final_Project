package com.jobpilot.api.domain.employer.service;

import com.jobpilot.api.domain.auth.service.JwtTokenService;
import com.jobpilot.api.domain.employer.client.NtsBusinessVerificationClient;
import com.jobpilot.api.domain.employer.dto.EmployerAuthResponse;
import com.jobpilot.api.domain.employer.dto.EmployerLoginRequest;
import com.jobpilot.api.domain.employer.dto.EmployerResponse;
import com.jobpilot.api.domain.employer.dto.EmployerProfileUpdateRequest;
import com.jobpilot.api.domain.employer.dto.EmployerSignupRequest;
import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.exception.DuplicateEmployerException;
import com.jobpilot.api.domain.employer.exception.InvalidEmployerCredentialsException;
import com.jobpilot.api.domain.employer.entity.EmployerPasswordlessStatus;
import com.jobpilot.api.domain.employer.repository.EmployerAccountRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 2026-08-19: 기업회원 가입/로그인. 개인회원(AuthService)과 완전히 분리된 계정
 * 체계라 이메일 인증 절차는 없고, 대신 가입 시점에 국세청 사업자 진위확인 API를
 * 바로 돌려 결과를 저장해 둔다 - 실제 계정 승인은 관리자가 별도로 처리한다
 * (EmployerAccessService 참고).
 */
@Service
@Transactional
public class EmployerAuthService {
    private final EmployerAccountRepository employers;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final NtsBusinessVerificationClient ntsClient;

    public EmployerAuthService(EmployerAccountRepository employers, PasswordEncoder passwordEncoder,
                                JwtTokenService tokenService, NtsBusinessVerificationClient ntsClient) {
        this.employers = employers;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.ntsClient = ntsClient;
    }

    public EmployerResponse signup(EmployerSignupRequest request) {
        if (employers.existsByLoginId(request.loginId())) throw new DuplicateEmployerException("이미 사용 중인 로그인 아이디입니다.");
        String email = normalizeEmail(request.email());
        if (employers.existsByEmail(email)) throw new DuplicateEmployerException("이미 사용 중인 이메일입니다.");
        String businessNumber = normalizeBusinessNumber(request.businessRegistrationNumber());
        if (employers.existsByBusinessRegistrationNumber(businessNumber)) {
            throw new DuplicateEmployerException("이미 등록된 사업자등록번호입니다.");
        }

        EmployerAccount employer = new EmployerAccount(
                request.loginId(), email, passwordEncoder.encode(request.password()),
                request.managerName(), request.managerPhone(), request.companyName(),
                businessNumber, request.representativeName(), request.openingDate(), request.companyAddress());

        NtsBusinessVerificationClient.Result verification =
                ntsClient.verify(businessNumber, request.openingDate(), request.representativeName());
        employer.applyNtsVerificationResult(verification.verified(), verification.rawResponse());

        return EmployerResponse.from(employers.save(employer));
    }

    public EmployerResponse me(Long employerId) {
        return EmployerResponse.from(employers.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다.")));
    }

    public EmployerAuthResponse login(EmployerLoginRequest request) {
        EmployerAccount employer = employers.findByLoginId(request.loginId())
                .orElseThrow(InvalidEmployerCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), employer.getPasswordHash()))
            throw new InvalidEmployerCredentialsException();
        if (employer.getPasswordlessStatus() == EmployerPasswordlessStatus.ACTIVE)
            throw new IllegalArgumentException("Passwordless 전환이 완료된 계정입니다. Passwordless 로그인으로 이용해 주세요.");
        if (!employer.isApproved())
            throw new IllegalArgumentException("관리자 승인이 완료된 기업회원만 로그인할 수 있습니다.");
        JwtTokenService.Token token = tokenService.issueForEmployer(employer);
        return new EmployerAuthResponse(token.value(), "Bearer", token.expiresInSeconds(), EmployerResponse.from(employer));
    }

    public EmployerResponse updateProfile(Long employerId, EmployerProfileUpdateRequest request) {
        EmployerAccount employer = employers.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다."));
        String loginId = request.loginId().trim();
        String email = normalizeEmail(request.email());
        if (employers.existsByLoginIdAndIdNot(loginId, employerId))
            throw new DuplicateEmployerException("이미 사용 중인 로그인 아이디입니다.");
        if (employers.existsByEmailAndIdNot(email, employerId))
            throw new DuplicateEmployerException("이미 사용 중인 이메일입니다.");
        String passwordHash = employer.getPasswordlessStatus() == EmployerPasswordlessStatus.ACTIVE
                || request.newPassword() == null || request.newPassword().isBlank()
                ? null : passwordEncoder.encode(request.newPassword());
        employer.updateProfile(loginId, email, passwordHash, request.managerName().trim(), blankToNull(request.managerPhone()),
                request.companyName().trim(), request.representativeName().trim(), request.openingDate(),
                blankToNull(request.companyAddress()));
        return EmployerResponse.from(employer);
    }

    public void withdraw(Long employerId) {
        EmployerAccount employer = employers.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다."));
        employer.withdraw();
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String normalizeBusinessNumber(String raw) { return raw.replaceAll("[^0-9]", ""); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
