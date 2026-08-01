package com.jobpilot.api.domain.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PlannerEventRequest(
        @NotBlank @Size(max = 30) String eventType,
        @NotBlank @Size(max = 500) String title,
        @NotNull LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay
) {}
