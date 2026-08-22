package com.jobpilot.api.domain.opportunity.controller;

import com.jobpilot.api.domain.opportunity.dto.OpportunityResponse;
import com.jobpilot.api.domain.opportunity.entity.Opportunity;
import com.jobpilot.api.domain.opportunity.repository.OpportunityRepository;
import com.jobpilot.api.domain.interest.repository.UserInterestRepository;
import com.jobpilot.api.global.security.AuthenticatedMember;
import org.springframework.security.core.Authentication;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private final OpportunityRepository repository;
    private final UserInterestRepository interests;

    public OpportunityController(OpportunityRepository repository, UserInterestRepository interests) {
        this.repository = repository; this.interests = interests;
    }

    @GetMapping("/recommended")
    public List<OpportunityResponse> recommended() {
        return repository.findByStatusOrderByDeadlineAtAsc("ACTIVE").stream()
                .map(this::toResponse)
                .toList();
    }
    @GetMapping("/bookmarked")
    public List<OpportunityResponse> bookmarked(Authentication auth) {
        return interests.findByMemberIdAndTargetTypeOrderByCreatedAtDesc(AuthenticatedMember.id(auth), "OPPORTUNITY").stream()
                .map(item -> repository.findById(item.getTargetId()).orElse(null)).filter(java.util.Objects::nonNull).map(this::toResponse).toList();
    }
    @GetMapping("/{id}")
    public OpportunityResponse detail(@PathVariable Long id) { return toResponse(repository.findById(id).orElseThrow(() -> new com.jobpilot.api.global.exception.ResourceNotFoundException("훈련과정을 찾을 수 없습니다."))); }

    private OpportunityResponse toResponse(Opportunity value) {
        return new OpportunityResponse(
                value.getId(), value.getType(), value.getTitle(), value.getOrganization(),
                period(value.getEventStartAt(), value.getEventEndAt()),
                format(value.getDeadlineAt()), reason(value), tags(value), value.getSourceUrl(), trainingStatus(value), value.getTrainingAddress(), value.getTrainingPhone(), value.getTrainingTarget(), value.getCapacity(), value.getEnrolledCount(), value.getCourseFee(), value.getSelfPayFee(), value.getSatisfactionScore(), value.getDetailUrl(), value.getInstitutionUrl(), value.getEventStartAt() == null ? null : value.getEventStartAt().toLocalDate().toString(), value.getTrainingNcsCode(), value.getTrainingContents(), value.getTrainingCertificate(), value.getTrainingGrade(), value.getEmploymentRate3m(), value.getEmploymentRate6m(), value.getThumbnailUrl()
        );
    }

    private String trainingStatus(Opportunity value) {
        if (!value.getType().equals("교육")) return value.getStatus();
        LocalDateTime now = LocalDateTime.now();
        if (value.getEventEndAt() != null && value.getEventEndAt().isBefore(now)) return "EXPIRED";
        if (value.getEventStartAt() != null && value.getEventStartAt().isBefore(now)) return "IN_PROGRESS";
        return value.getStatus();
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
