package com.jobpilot.api.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

//서큐리티 큰피구
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.dev-auth.enabled:false}") boolean developmentAuthenticationEnabled,
            InternalApiKeyFilter internalApiKeyFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/login-id-availability",
                            "/api/v1/auth/email-verifications/**", "/api/v1/health", "/error", "/ws/camera-pair").permitAll();

//                     임시!@@@@#@@@@@ 지도용
                    authorize.requestMatchers("/api/location-jobs/**").permitAll();

                    authorize.requestMatchers("/api/tests/**").permitAll(); // 심리검사테스트 비로그인자도 확인하도록 임시용
                    authorize.requestMatchers("/api/checks/**", "/api/v1/auth/**").permitAll(); // 맞춤법 검사기 비로그인자도 가능하게 확인용

                    if (developmentAuthenticationEnabled) {
                        authorize.requestMatchers("/api/v1/dev/auth/token").permitAll();
                    }
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/job-postings/**").permitAll();
                    // POST /ingest는 크롤러(ai-server)가 로그인 토큰 없이 호출한다 -
                    // InternalApiKeyFilter가 공유 비밀키(app.internal-api-key)로 이미
                    // 검증하므로 여기서는 permitAll하고, 키 검증 실패는 그 필터가 401로
                    // 먼저 끊어낸다 (2026-08-04: 이게 안 열려있어서 크롤링 2836건 성공하고도
                    // DB엔 0건 저장되는 버그가 있었음).
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/job-postings/ingest").permitAll();
                    authorize.anyRequest().authenticated();
                })
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
