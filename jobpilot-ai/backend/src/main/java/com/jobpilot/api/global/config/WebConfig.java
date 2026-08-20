package com.jobpilot.api.global.config;

import com.jobpilot.api.domain.admin.AdminFaceVerificationInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final List<String> allowedOrigins;
    private final AdminFaceVerificationInterceptor adminFaceVerificationInterceptor;

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins,
                     AdminFaceVerificationInterceptor adminFaceVerificationInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.adminFaceVerificationInterceptor = adminFaceVerificationInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(adminFaceVerificationInterceptor)
                .addPathPatterns("/api/v1/admin/**")
                // These three endpoints are the QR flow that creates and proves the face session.
                .excludePathPatterns("/api/v1/admin/face-pairings/**");
    }
}
