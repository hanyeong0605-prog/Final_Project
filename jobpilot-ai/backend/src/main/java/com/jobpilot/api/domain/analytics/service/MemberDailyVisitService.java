package com.jobpilot.api.domain.analytics.service;

import com.jobpilot.api.domain.analytics.entity.MemberDailyVisit;
import com.jobpilot.api.domain.analytics.repository.MemberDailyVisitRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberDailyVisitService {
    private final MemberDailyVisitRepository visits;

    public MemberDailyVisitService(MemberDailyVisitRepository visits) { this.visits = visits; }

    public void record(Long memberId) {
        LocalDate today = LocalDate.now();
        if (!visits.existsByMemberIdAndVisitDate(memberId, today)) {
            visits.save(new MemberDailyVisit(memberId, today));
        }
    }

    @Transactional(readOnly = true)
    public DailyVisitorSummary today() {
        LocalDate today = LocalDate.now();
        return new DailyVisitorSummary(
                visits.countDistinctVisitorsByDate(today),
                visits.countDistinctVisitorsByDateAndRole(today, "USER"),
                visits.countDistinctVisitorsByDateAndRole(today, "ADMIN"));
    }

    public record DailyVisitorSummary(long total, long users, long admins) {}
}
