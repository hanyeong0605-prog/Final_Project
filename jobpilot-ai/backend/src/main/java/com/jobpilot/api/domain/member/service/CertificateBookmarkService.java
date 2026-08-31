package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.CertificateBookmarkRequest;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.entity.CertificateBookmark;
import com.jobpilot.api.domain.member.repository.CertificateBookmarkRepository;
import com.jobpilot.api.domain.planner.entity.PlannerEvent;
import com.jobpilot.api.domain.planner.repository.PlannerEventRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 2026-08-11: "성장 기회 추천" 페이지 - 회원이 찜해 둔 Q-Net 자격 종목 CRUD.
 * 응답 모양을 {@link QnetQualificationResponse}(카탈로그 검색 결과와 동일)로 맞춰서,
 * 프론트에서 검색 결과 카드/상세보기 모달을 그대로 재사용할 수 있게 한다.
 */
@Service
@Transactional
public class CertificateBookmarkService {
    private static final int MAX_BOOKMARKS = 50;

    private final CertificateBookmarkRepository repository;
    private final QnetQualificationService qnet;
    private final PlannerEventRepository plannerEvents;

    public CertificateBookmarkService(CertificateBookmarkRepository repository, QnetQualificationService qnet, PlannerEventRepository plannerEvents) {
        this.repository = repository;
        this.qnet = qnet;
        this.plannerEvents = plannerEvents;
    }

    public List<QnetQualificationResponse> list(Long memberId) {
        return repository.findByMemberIdOrderByIdDesc(memberId).stream().map(this::toResponse).toList();
    }

    public void syncPlannerEvents(Long memberId) {
        repository.findByMemberIdOrderByIdDesc(memberId).stream()
                .filter(bookmark -> !plannerEvents.existsByMemberIdAndSourceTypeAndSourceId(memberId, "CERTIFICATE", bookmark.getId()))
                .forEach(bookmark -> syncExamSchedule(memberId, bookmark));
    }

    public List<QnetQualificationResponse> add(Long memberId, CertificateBookmarkRequest request) {
        if (repository.findByMemberIdAndJmcd(memberId, request.jmcd()).isEmpty()) {
            if (repository.findByMemberIdOrderByIdDesc(memberId).size() >= MAX_BOOKMARKS) {
                throw new IllegalArgumentException("찜한 자격증은 최대 " + MAX_BOOKMARKS + "개까지 저장할 수 있습니다.");
            }
            CertificateBookmark bookmark = repository.save(new CertificateBookmark(memberId, request.jmcd(), request.name(),
                    blankToNull(request.qualificationType()), blankToNull(request.field()), blankToNull(request.subField())));
            syncExamSchedule(memberId, bookmark);
        }
        return list(memberId);
    }

    public List<QnetQualificationResponse> remove(Long memberId, String jmcd) {
        repository.findByMemberIdAndJmcd(memberId, jmcd)
                .ifPresent(bookmark -> plannerEvents.deleteByMemberIdAndSourceTypeAndSourceId(memberId, "CERTIFICATE", bookmark.getId()));
        repository.deleteByMemberIdAndJmcd(memberId, jmcd);
        return list(memberId);
    }

    /** Removes the bookmark and every certificate schedule generated from it. */
    public void removeById(Long memberId, Long bookmarkId) {
        repository.findByIdAndMemberId(bookmarkId, memberId).ifPresent(bookmark -> {
            plannerEvents.deleteByMemberIdAndSourceTypeAndSourceId(memberId, "CERTIFICATE", bookmark.getId());
            repository.delete(bookmark);
        });
    }

    private void syncExamSchedule(Long memberId, CertificateBookmark bookmark) {
        try {
            qnet.detail(bookmark.getJmcd()).rounds().forEach(round -> {
                saveExamRange(memberId, bookmark, round.roundName(), "필기시험", "CERTIFICATE_WRITTEN", round.writtenExamStart(), round.writtenExamEnd());
                saveExamRange(memberId, bookmark, round.roundName(), "실기시험", "CERTIFICATE_PRACTICAL", round.practicalExamStart(), round.practicalExamEnd());
            });
        } catch (RuntimeException ignored) {
            // Q-Net 일시 장애가 찜 저장까지 막지 않도록 한다. 상세/플래너 재조회 때 다시 확인할 수 있다.
        }
    }

    private void saveExamRange(Long memberId, CertificateBookmark bookmark, String roundName, String phase, String eventType, String start, String end) {
        LocalDate from = parseDate(start); if (from == null) return;
        LocalDate to = parseDate(end); if (to == null) to = from;
        String title = "자격증 · " + bookmark.getName() + " · " + (roundName == null || roundName.isBlank() ? phase : roundName + " " + phase);
        LocalDateTime startsAt = LocalDateTime.of(from, LocalTime.MIN);
        // One qualification has multiple annual rounds. The old unique key treated all
        // written (or all practical) rounds as one event, causing a flush failure that
        // made the entire planner GET endpoint return 500.
        if (plannerEvents.existsByMemberIdAndSourceTypeAndSourceIdAndEventTypeAndStartsAt(
                memberId, "CERTIFICATE", bookmark.getId(), eventType, startsAt)) return;
        plannerEvents.save(PlannerEvent.fromCertificate(memberId, bookmark.getId(), title, eventType,
                startsAt, LocalDateTime.of(to, LocalTime.MAX)));
    }

    private LocalDate parseDate(String value) {
        try { return value == null || value.isBlank() ? null : LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE); }
        catch (RuntimeException ignored) { return null; }
    }

    private QnetQualificationResponse toResponse(CertificateBookmark bookmark) {
        return new QnetQualificationResponse(bookmark.getJmcd(), bookmark.getName(),
                bookmark.getQualificationType(), bookmark.getField(), bookmark.getSubField());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
