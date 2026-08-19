package com.jobpilot.api.domain.auth.controller;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.service.LocalDevAuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@ConditionalOnProperty(prefix = "app.dev-auth", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/api/v1/dev/auth")
public class LocalDevAuthController {
    private final LocalDevAuthService service;

    public LocalDevAuthController(LocalDevAuthService service) {
        this.service = service;
    }

    @PostMapping("/token")
    public AuthResponse issueDevelopmentToken() {
        return service.issueDevelopmentToken();
    }

    @PostMapping("/admin-token")
    public AuthResponse issueDevelopmentAdminToken() {
        return service.issueDevelopmentAdminToken();
    }
}
