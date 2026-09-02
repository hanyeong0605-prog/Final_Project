package com.jobpilot.api.domain.employer.controller;

import com.jobpilot.api.domain.employer.dto.EmployerJobPostingRequest;
import com.jobpilot.api.domain.employer.dto.EmployerJobPostingResponse;
import com.jobpilot.api.domain.employer.service.EmployerJobPostingService;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employer/job-postings")
public class EmployerJobPostingController {
    private final EmployerJobPostingService service;

    public EmployerJobPostingController(EmployerJobPostingService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EmployerJobPostingResponse> myPostings(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long employerId = AuthenticatedEmployer.id(authentication);
        return PageResponse.from(service.myPostings(employerId, page, size));
    }

    @PostMapping
    public EmployerJobPostingResponse create(Authentication authentication, @Valid @RequestBody EmployerJobPostingRequest request) {
        return service.create(AuthenticatedEmployer.id(authentication), request);
    }

    @PutMapping("/{jobPostingId}")
    public EmployerJobPostingResponse update(Authentication authentication, @PathVariable Long jobPostingId,
                                              @Valid @RequestBody EmployerJobPostingRequest request) {
        return service.update(AuthenticatedEmployer.id(authentication), jobPostingId, request);
    }

    @DeleteMapping("/{jobPostingId}")
    public void delete(Authentication authentication, @PathVariable Long jobPostingId) {
        service.delete(AuthenticatedEmployer.id(authentication), jobPostingId);
    }

    public record PageResponse<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
