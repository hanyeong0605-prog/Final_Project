package com.jobpilot.api.domain.member.controller;
import com.jobpilot.api.domain.employer.service.EmployerTalentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/members/me/talent-visibility")
public class MemberTalentVisibilityController {
    private final EmployerTalentService service; public MemberTalentVisibilityController(EmployerTalentService service) { this.service = service; }
    @GetMapping public Map<String, Boolean> get(Authentication auth) { return Map.of("enabled", service.visibility(AuthenticatedMember.id(auth))); }
    @PutMapping public Map<String, Boolean> update(Authentication auth, @RequestBody Map<String, Boolean> request) { return Map.of("enabled", service.changeVisibility(AuthenticatedMember.id(auth), Boolean.TRUE.equals(request.get("enabled")))); }
}
