package com.jobpilot.api.domain.opportunity.controller;

import com.jobpilot.api.domain.opportunity.dto.OpportunityResponse;
import com.jobpilot.api.domain.opportunity.entity.Opportunity;
import com.jobpilot.api.domain.opportunity.repository.OpportunityRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private final OpportunityRepository repository;

    public OpportunityController(OpportunityRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/recommended")
    public List<OpportunityResponse> recommended() {
        return repository.findByStatusOrderByDeadlineAtAsc("ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }

    private OpportunityResponse toResponse(Opportunity value) {
        return new OpportunityResponse(
                value.getId(), value.getType(), value.getTitle(), value.getOrganization(),
                period(value.getEventStartAt(), value.getEventEndAt()),
                format(value.getDeadlineAt()), reason(value), tags(value)
        );
    }

    private String period(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return "";
        if (end == null) return format(start);
        if (start == null) return format(end);
        return format(start) + " - " + format(end);
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DATE.format(value);
    }
    private String reason(Opportunity value) { return value.getDescription() == null ? "" : value.getDescription().replaceFirst("WORK24_IT_TAGS=.*?; ", ""); }
    private List<String> tags(Opportunity value) {
        String description=value.getDescription(); if (description == null || !description.startsWith("WORK24_IT_TAGS=")) return List.of();
        int end=description.indexOf(';'); if (end < 0) return List.of(); String raw=description.substring("WORK24_IT_TAGS=".length(), end);
        return raw.isBlank() ? List.of() : List.of(raw.split(","));
    }
}
