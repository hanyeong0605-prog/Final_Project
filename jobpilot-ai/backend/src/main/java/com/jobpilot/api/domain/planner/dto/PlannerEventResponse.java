package com.jobpilot.api.domain.planner.dto;

import java.time.LocalDateTime;

public record PlannerEventResponse(
        Long id, String tone, String time, String title, String body,
        String eventType, LocalDateTime startsAt, LocalDateTime endsAt,
        boolean allDay, boolean editable
) {}
