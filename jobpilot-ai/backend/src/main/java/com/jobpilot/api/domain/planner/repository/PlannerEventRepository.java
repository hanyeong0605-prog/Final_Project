package com.jobpilot.api.domain.planner.repository;

import com.jobpilot.api.domain.planner.entity.PlannerEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlannerEventRepository extends JpaRepository<PlannerEvent, Long> {
    @Query("select e from PlannerEvent e where e.memberId = :memberId and e.startsAt < :to " +
            "and (e.endsAt is null and e.startsAt >= :from or e.endsAt >= :from) order by e.startsAt asc")
    List<PlannerEvent> findOverlapping(@Param("memberId") Long memberId,
                                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    Optional<PlannerEvent> findByIdAndMemberId(Long id, Long memberId);
    Optional<PlannerEvent> findByMemberIdAndSourceTypeAndSourceIdAndEventType(
            Long memberId, String sourceType, Long sourceId, String eventType);
    void deleteByMemberIdAndSourceTypeAndSourceId(Long memberId, String sourceType, Long sourceId);
}
