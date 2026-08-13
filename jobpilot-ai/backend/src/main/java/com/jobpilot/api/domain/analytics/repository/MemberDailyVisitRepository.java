package com.jobpilot.api.domain.analytics.repository;

import com.jobpilot.api.domain.analytics.entity.MemberDailyVisit;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberDailyVisitRepository extends JpaRepository<MemberDailyVisit, Long> {
    boolean existsByMemberIdAndVisitDate(Long memberId, LocalDate visitDate);

    @Query(value = """
            SELECT COUNT(DISTINCT visit.member_id)
            FROM member_daily_visits visit
            JOIN members member ON member.id = visit.member_id
            WHERE visit.visit_date = :visitDate AND member.role = :role
            """, nativeQuery = true)
    long countDistinctVisitorsByDateAndRole(@Param("visitDate") LocalDate visitDate, @Param("role") String role);

    @Query(value = "SELECT COUNT(DISTINCT member_id) FROM member_daily_visits WHERE visit_date = :visitDate", nativeQuery = true)
    long countDistinctVisitorsByDate(@Param("visitDate") LocalDate visitDate);
}
