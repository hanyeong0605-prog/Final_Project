package com.jobpilot.api.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.jobpilot.api.domain.auth.service.OAuthAuthenticationSuccessHandler;

//서큐리티 큰피구
@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.dev-auth.enabled:false}") boolean developmentAuthenticationEnabled,
            InternalApiKeyFilter internalApiKeyFilter,
            OAuthReturnToRequestFilter oauthReturnToRequestFilter,
            OAuthAuthenticationSuccessHandler oauthSuccessHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // OAuth uses a short-lived session for the authorization state; APIs still authenticate by JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(oauthReturnToRequestFilter, OAuth2AuthorizationRequestRedirectFilter.class)
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/login-id-availability",
                            "/api/v1/auth/email-verifications/**", "/api/v1/health", "/error", "/ws/camera-pair").permitAll();
                    // 2026-08-19: 기업회원 가입/로그인 - 개인회원과 별도 계정 체계라 로그인 전에는
                    // 당연히 permitAll이어야 하고, /me와 채용공고 등록은 인증 필요(anyRequest.authenticated).
                    authorize.requestMatchers("/api/v1/employer/auth/signup", "/api/v1/employer/auth/login",
                            "/api/v1/employer/auth/login-id-availability").permitAll();
                    authorize.requestMatchers("/api/v1/employer/passwordless/**").permitAll();
                    authorize.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();

//                     임시!@@@@#@@@@@ 지도용
                    authorize.requestMatchers("/api/location-jobs/**").permitAll();
                    // CareerNet school/major lookup contains public reference data only.
                    // Keeping it public also lets the profile form work before a stale JWT is refreshed.
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/education/**").permitAll();
                    // 2026-08-11: catalog/detail 둘 다 로그인 사용자 개인정보가 아니라 Q-Net
                    // 공개 조회 데이터라, 스펙 편집 중 JWT 만료돼도 끊기지 않게 permitAll 처리.
                    // (KCA 진위확인 기능은 커버 종목이 너무 적어 실효성이 없어 제거함 - 2026-08-11)
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/certifications/catalog", "/api/v1/certifications/catalog/list", "/api/v1/certifications/catalog/fields", "/api/v1/certifications/catalog/*/detail").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/books").permitAll();

                    authorize.requestMatchers("/api/v1/auth/**").permitAll();

                    if (developmentAuthenticationEnabled) {
                        authorize.requestMatchers("/api/v1/dev/auth/token", "/api/v1/dev/auth/admin-token").permitAll();
                    }
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/job-postings/**").permitAll();
                    // POST /ingest는 크롤러(ai-server)가 로그인 토큰 없이 호출한다 -
                    // InternalApiKeyFilter가 공유 비밀키(app.internal-api-key)로 이미
                    // 검증하므로 여기서는 permitAll하고, 키 검증 실패는 그 필터가 401로
                    // 먼저 끊어낸다 (2026-08-04: 이게 안 열려있어서 크롤링 2836건 성공하고도
                    // DB엔 0건 저장되는 버그가 있었음).
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/job-postings/ingest").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/job-postings/crawl-runs/**").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2Login(oauth -> oauth
                        .successHandler(oauthSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            log.error("OAuth provider authentication failed: uri={}", request.getRequestURI(), exception);
                            response.sendRedirect("/login?socialError=OAUTH_PROVIDER_FAILED");
                        }))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey, @Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
