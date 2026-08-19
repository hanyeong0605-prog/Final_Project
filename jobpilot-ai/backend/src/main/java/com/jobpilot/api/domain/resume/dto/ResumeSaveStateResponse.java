package com.jobpilot.api.domain.resume.dto;

import java.time.LocalDateTime;

public record ResumeSaveStateResponse(String status, LocalDateTime updatedAt) {}
