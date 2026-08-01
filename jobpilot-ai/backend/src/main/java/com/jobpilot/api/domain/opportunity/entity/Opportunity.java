package com.jobpilot.api.domain.opportunity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "opportunities")
public class Opportunity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String type;
    @Column(name = "source_name", nullable = false) private String sourceName;
    @Column(name = "external_id") private String externalId;
    @Column(nullable = false) private String title;
    private String organization;
    @Lob @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "source_url", nullable = false, length = 1500) private String sourceUrl;
    @Column(name = "application_start_at") private LocalDateTime applicationStartAt;
    @Column(name = "deadline_at") private LocalDateTime deadlineAt;
    @Column(name = "event_start_at") private LocalDateTime eventStartAt;
    @Column(name = "event_end_at") private LocalDateTime eventEndAt;
    @Column(nullable = false) private String status;

    protected Opportunity() {}

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getOrganization() { return organization; }
    public String getDescription() { return description; }
    public LocalDateTime getApplicationStartAt() { return applicationStartAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public LocalDateTime getEventStartAt() { return eventStartAt; }
    public LocalDateTime getEventEndAt() { return eventEndAt; }
}
