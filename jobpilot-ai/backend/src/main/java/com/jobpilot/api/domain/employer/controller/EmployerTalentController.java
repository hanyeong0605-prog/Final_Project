package com.jobpilot.api.domain.employer.controller;
import com.jobpilot.api.domain.employer.service.EmployerTalentService;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/employer/talents")
public class EmployerTalentController {
    private final EmployerTalentService service; public EmployerTalentController(EmployerTalentService service) { this.service = service; }
    @GetMapping public List<EmployerTalentService.Talent> list(Authentication auth, @RequestParam(defaultValue = "") String query) { return service.list(AuthenticatedEmployer.id(auth), query); }
    @GetMapping("/favorites") public List<EmployerTalentService.Talent> favorites(Authentication auth) { return service.favorites(AuthenticatedEmployer.id(auth)); }
    @GetMapping("/{memberId}") public EmployerTalentService.Talent detail(Authentication auth, @PathVariable Long memberId) { return service.detail(AuthenticatedEmployer.id(auth), memberId); }
    @PostMapping("/{memberId}/favorite") public Map<String, Boolean> favorite(Authentication auth, @PathVariable Long memberId) { return Map.of("favorite", service.toggleFavorite(AuthenticatedEmployer.id(auth), memberId)); }
}
