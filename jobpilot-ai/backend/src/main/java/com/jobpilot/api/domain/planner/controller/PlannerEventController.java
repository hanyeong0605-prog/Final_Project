package com.jobpilot.api.domain.planner.controller;

import com.jobpilot.api.domain.planner.dto.PlannerEventRequest;
import com.jobpilot.api.domain.planner.dto.PlannerEventResponse;
import com.jobpilot.api.domain.planner.service.PlannerEventService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planner-events")
public class PlannerEventController {
    private final PlannerEventService service;
    public PlannerEventController(PlannerEventService service) { this.service = service; }

    @GetMapping
    public List<PlannerEventResponse> find(Authentication auth, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.find(AuthenticatedMember.id(auth), from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlannerEventResponse create(Authentication auth, @Valid @RequestBody PlannerEventRequest request) {
        return service.create(AuthenticatedMember.id(auth), request);
    }

    @PutMapping("/{eventId}")
    public PlannerEventResponse update(Authentication auth, @PathVariable Long eventId,
                                       @Valid @RequestBody PlannerEventRequest request) {
        return service.update(AuthenticatedMember.id(auth), eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long eventId) {
        service.delete(AuthenticatedMember.id(auth), eventId);
    }
}
