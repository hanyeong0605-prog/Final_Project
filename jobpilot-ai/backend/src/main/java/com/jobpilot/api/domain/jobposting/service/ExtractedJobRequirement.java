package com.jobpilot.api.domain.jobposting.service;

record ExtractedJobRequirement(
        String type,
        String content,
        String importance,
        String sourceExcerpt
) {
    boolean isSkill() {
        return "SKILL".equals(type);
    }
}
