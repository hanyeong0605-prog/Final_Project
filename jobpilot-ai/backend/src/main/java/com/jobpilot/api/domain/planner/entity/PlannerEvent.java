package com.jobpilot.api.domain.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "planner_events")
public class PlannerEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "source_id") private Long sourceId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false) private String title;
    @Column(name = "starts_at", nullable = false) private LocalDateTime startsAt;
    @Column(name = "ends_at") private LocalDateTime endsAt;
    @Column(name = "all_day", nullable = false) private boolean allDay;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected PlannerEvent() {}

    public PlannerEvent(Long memberId, String eventType, String title, LocalDateTime startsAt,
                        LocalDateTime endsAt, boolean allDay) {
        this.memberId = memberId;
        this.sourceType = "MANUAL";
        this.sourceId = null;
        this.eventType = eventType;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allDay = allDay;
        this.createdAt = LocalDateTime.now();
    }

    public static PlannerEvent fromJobPosting(Long memberId, Long jobPostingId, String title,
                                               LocalDateTime startsAt, LocalDateTime endsAt) {
        PlannerEvent event = new PlannerEvent();
        event.memberId = memberId;
        event.sourceType = "JOB_POSTING";
        event.sourceId = jobPostingId;
        event.eventType = "APPLICATION_PERIOD";
        event.title = title;
        event.startsAt = startsAt;
        event.endsAt = endsAt;
        event.allDay = true;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getSourceType() { return sourceType; }
    public String getEventType() { return eventType; }
    public String getTitle() { return title; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public boolean isAllDay() { return allDay; }
    public boolean isManual() { return "MANUAL".equals(sourceType); }

    public void update(String eventType, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {
        this.eventType = eventType;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allDay = allDay;
    }
}
