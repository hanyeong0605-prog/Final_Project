package com.jobpilot.api.domain.planner.service;

import com.jobpilot.api.domain.planner.dto.PlannerEventRequest;
import com.jobpilot.api.domain.planner.dto.PlannerEventResponse;
import com.jobpilot.api.domain.planner.entity.PlannerEvent;
import com.jobpilot.api.domain.planner.repository.PlannerEventRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.domain.member.service.CertificateBookmarkService;
import com.jobpilot.api.domain.interest.service.InterestService;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PlannerEventService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private final PlannerEventRepository repository;
    private final CertificateBookmarkService certificateBookmarks;
    private final InterestService interests;

    public PlannerEventService(PlannerEventRepository repository, CertificateBookmarkService certificateBookmarks,
                               InterestService interests) {
        this.repository = repository;
        this.certificateBookmarks = certificateBookmarks;
        this.interests = interests;
    }

    public List<PlannerEventResponse> find(Long memberId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        certificateBookmarks.syncPlannerEvents(memberId);
        return repository.findOverlapping(
                memberId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream().map(this::response).toList();
    }

    public PlannerEventResponse create(Long memberId, PlannerEventRequest request) {
        validate(request);
        return response(repository.save(new PlannerEvent(memberId, normalizeType(request.eventType()), request.title().trim(),
                request.startsAt(), request.endsAt(), request.allDay())));
    }

    public PlannerEventResponse update(Long memberId, Long eventId, PlannerEventRequest request) {
        validate(request);
        PlannerEvent event = editable(memberId, eventId);
        event.update(normalizeType(request.eventType()), request.title().trim(), request.startsAt(), request.endsAt(), request.allDay());
        return response(event);
    }

    public void delete(Long memberId, Long eventId) {
        PlannerEvent event = repository.findByIdAndMemberId(eventId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다."));
        if (event.isManual()) {
            repository.delete(event);
            return;
        }
        if ("JOB_POSTING".equals(event.getSourceType()) && event.getSourceId() != null) {
            interests.removeJobBookmark(memberId, event.getSourceId());
            return;
        }
        if ("CERTIFICATE".equals(event.getSourceType()) && event.getSourceId() != null) {
            certificateBookmarks.removeById(memberId, event.getSourceId());
            return;
        }
        throw new IllegalArgumentException("이 자동 일정은 원본 관심 목록에서 관리해 주세요.");
    }

    private PlannerEvent editable(Long memberId, Long id) {
        PlannerEvent event = repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다."));
        if (!event.isManual()) throw new IllegalArgumentException("관심 항목에서 생성된 일정은 해당 관심 항목에서 관리해 주세요.");
        return event;
    }

    private void validate(PlannerEventRequest request) {
        if (request.endsAt() != null && request.endsAt().isBefore(request.startsAt()))
            throw new IllegalArgumentException("일정 종료 시각은 시작 시각보다 빠를 수 없습니다.");
    }

    private String normalizeType(String value) { return value.trim().toUpperCase(); }

    private PlannerEventResponse response(PlannerEvent event) {
        return new PlannerEventResponse(event.getId(), tone(event.getSourceType(), event.getEventType()), TIME.format(event.getStartsAt()),
                event.getTitle(), event.getEventType(), event.getEventType(), event.getStartsAt(), event.getEndsAt(),
                event.isAllDay(), event.isManual(), event.getSourceType(), event.getSourceId());
    }

    private String tone(String sourceType, String eventType) {
        if ("JOB_POSTING".equals(sourceType)) return "blue";
        if ("CERTIFICATE".equals(sourceType)) return "purple";
        return switch (eventType) {
            case "APPLICATION", "INTERVIEW" -> "blue";
            case "STUDY", "CERTIFICATE", "TRAINING_PERIOD" -> "purple";
            default -> "orange";
        };
    }
}
