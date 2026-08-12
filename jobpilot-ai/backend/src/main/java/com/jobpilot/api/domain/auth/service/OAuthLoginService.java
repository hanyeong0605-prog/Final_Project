package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.auth.dto.OAuthCompleteRequest;
import com.jobpilot.api.domain.auth.entity.MemberOAuthAccount;
import com.jobpilot.api.domain.auth.entity.OAuthPendingLogin;
import com.jobpilot.api.domain.auth.entity.OAuthProvider;
import com.jobpilot.api.domain.auth.repository.MemberOAuthAccountRepository;
import com.jobpilot.api.domain.auth.repository.OAuthPendingLoginRepository;
import com.jobpilot.api.domain.auth.exception.EmailVerificationException;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.member.service.MemberConsentService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OAuthLoginService {
    private final MemberOAuthAccountRepository accounts;
    private final OAuthPendingLoginRepository pendingLogins;
    private final MemberRepository members;
    private final JwtTokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final MemberConsentService consents;
    private final long pendingExpiryMinutes;

    public OAuthLoginService(MemberOAuthAccountRepository accounts, OAuthPendingLoginRepository pendingLogins,
                             MemberRepository members, JwtTokenService tokens, PasswordEncoder passwordEncoder,
                             EmailVerificationService emailVerificationService, MemberConsentService consents,
                             @Value("${app.oauth.pending-expiry-minutes:10}") long pendingExpiryMinutes) {
        this.accounts = accounts; this.pendingLogins = pendingLogins; this.members = members; this.tokens = tokens;
        this.passwordEncoder = passwordEncoder; this.emailVerificationService = emailVerificationService;
        this.consents = consents; this.pendingExpiryMinutes = pendingExpiryMinutes;
    }

    public LoginResult begin(String registrationId, Map<String, Object> attributes) {
        OAuthProvider provider = OAuthProvider.fromRegistrationId(registrationId);
        ProviderProfile profile = ProviderProfile.from(provider, attributes);
        return accounts.findByProviderAndProviderSubject(provider, profile.subject())
                .<LoginResult>map(account -> LoginResult.completed(response(account.getMember())))
                .orElseGet(() -> beginNew(provider, profile));
    }

    public AuthResponse complete(OAuthCompleteRequest request) {
        OAuthPendingLogin pending = pendingLogins.findById(request.ticket())
                .filter(OAuthPendingLogin::isUsable)
                .orElseThrow(() -> new EmailVerificationException("소셜 로그인 확인 시간이 만료되었습니다. 다시 로그인해 주세요."));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        boolean trustedProviderEmail = pending.getProviderEmail() != null
                && pending.getProviderEmail().equalsIgnoreCase(email);
        if (!trustedProviderEmail) {
            emailVerificationService.consumeVerifiedEmail(email, request.emailVerificationToken());
        }
        Member member = members.findByEmail(email).orElseGet(() -> members.save(new Member(
                nextLoginId(pending.getProvider()), email, passwordEncoder.encode(UUID.randomUUID().toString()), pending.getNickname())));
        if (accounts.findByProviderAndProviderSubject(pending.getProvider(), pending.getProviderSubject()).isEmpty()) {
            accounts.save(new MemberOAuthAccount(member, pending.getProvider(), pending.getProviderSubject()));
        }
        if (!member.isOnboardingCompleted()) {
            consents.recordOAuthSignupConsents(member, request.termsAgreed(), request.privacyCollectionAgreed(), request.marketingEmailAgreed());
        }
        pending.consume();
        return response(member);
    }

    private LoginResult beginNew(OAuthProvider provider, ProviderProfile profile) {
        if (profile.email() != null) {
            Member existing = members.findByEmail(profile.email()).orElse(null);
            if (existing != null) {
                accounts.save(new MemberOAuthAccount(existing, provider, profile.subject()));
                return LoginResult.completed(response(existing));
            }
        }
        OAuthPendingLogin pending = pendingLogins.findByProviderAndProviderSubject(provider, profile.subject()).orElse(null);
        if (pending != null && !pending.isUsable()) {
            // Flush the delete before creating a replacement: provider + subject is unique.
            pendingLogins.delete(pending);
            pendingLogins.flush();
            pending = null;
        }
        if (pending == null) {
            pending = pendingLogins.save(new OAuthPendingLogin(provider, profile.subject(), profile.nickname(), profile.email(),
                    LocalDateTime.now().plusMinutes(pendingExpiryMinutes)));
        }
        return LoginResult.pending(pending.getId(), profile.nickname(), profile.email(), provider.name().toLowerCase());
    }

    private AuthResponse response(Member member) {
        JwtTokenService.Token token = tokens.issue(member);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), MemberResponse.from(member));
    }

    private String nextLoginId(OAuthProvider provider) {
        String candidate;
        do { candidate = "oauth-" + provider.name().toLowerCase() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
        while (members.existsByLoginId(candidate));
        return candidate;
    }

    public record LoginResult(AuthResponse response, String ticket, String nickname, String email, String provider) {
        static LoginResult completed(AuthResponse response) { return new LoginResult(response, null, null, null, null); }
        static LoginResult pending(String ticket, String nickname, String email, String provider) { return new LoginResult(null, ticket, nickname, email, provider); }
        public boolean isCompleted() { return response != null; }
    }

    private record ProviderProfile(String subject, String nickname, String email) {
        static ProviderProfile from(OAuthProvider provider, Map<String, Object> attributes) {
            Map<String, Object> root = nested(attributes, "response");
            if (provider == OAuthProvider.KAKAO) {
                Map<String, Object> properties = nested(attributes, "properties");
                Map<String, Object> account = nested(attributes, "kakao_account");
                return new ProviderProfile(value(attributes, "id"), defaultNickname(optionalValue(properties, "nickname")), normalize(optionalValue(account, "email")));
            }
            return new ProviderProfile(value(root, provider == OAuthProvider.GOOGLE ? "sub" : "id"),
                    defaultNickname(first(optionalValue(root, "name"), optionalValue(root, "nickname"))), normalize(optionalValue(root, "email")));
        }
        private static Map<String, Object> nested(Map<String, Object> source, String key) {
            Object value = source.get(key); return value instanceof Map<?, ?> map ? (Map<String, Object>) map : source;
        }
        private static String value(Map<String, Object> source, String key) {
            Object value = source.get(key); if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("소셜 계정 식별 정보를 가져오지 못했습니다."); return value.toString();
        }
        private static String optionalValue(Map<String, Object> source, String key) {
            Object value = source.get(key);
            return value == null || value.toString().isBlank() ? null : value.toString();
        }
        private static String first(String first, String second) { return first == null || first.isBlank() ? second : first; }
        private static String defaultNickname(String value) { return value == null || value.isBlank() ? "Job-A-Dream 사용자" : value.substring(0, Math.min(80, value.length())); }
        private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    }
}
