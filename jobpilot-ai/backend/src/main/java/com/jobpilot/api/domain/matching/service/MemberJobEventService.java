package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.matching.entity.MemberJobEvent;
import com.jobpilot.api.domain.matching.repository.MemberJobEventRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberJobEventService {
    private static final Set<String> ALLOWED = Set.of("VIEW_DETAIL", "OPEN_SOURCE", "BOOKMARK", "HIDE", "APPLY_CLICK");
    private final MemberJobEventRepository events;

    public MemberJobEventService(MemberJobEventRepository events) { this.events = events; }

    public void record(Long memberId, Long jobPostingId, String eventType) {
        if (ALLOWED.contains(eventType)) events.save(new MemberJobEvent(memberId, jobPostingId, eventType));
    }
}
