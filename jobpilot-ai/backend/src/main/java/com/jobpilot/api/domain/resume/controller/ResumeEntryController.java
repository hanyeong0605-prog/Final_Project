package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeEntryRequest;
import com.jobpilot.api.domain.resume.dto.ResumeEntryResponse;
import com.jobpilot.api.domain.resume.service.ResumeEntryService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/members/me/resume-entries")
public class ResumeEntryController {
    private final ResumeEntryService service;
    public ResumeEntryController(ResumeEntryService service) { this.service = service; }
    @GetMapping public List<ResumeEntryResponse> list(Authentication auth) { return service.list(AuthenticatedMember.id(auth)); }
    @PostMapping public ResumeEntryResponse create(Authentication auth, @Valid @RequestBody ResumeEntryRequest request) { return service.create(AuthenticatedMember.id(auth), request); }
    @PutMapping("/{id}") public ResumeEntryResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ResumeEntryRequest request) { return service.update(AuthenticatedMember.id(auth), id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) { service.delete(AuthenticatedMember.id(auth), id); return ResponseEntity.noContent().build(); }
}
