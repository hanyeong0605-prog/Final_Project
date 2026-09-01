package com.jobpilot.api.domain.opportunity.repository;

import com.jobpilot.api.domain.opportunity.entity.Opportunity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findByStatusOrderByDeadlineAtAsc(String status);

    @Query("SELECT o FROM Opportunity o WHERE o.type = '교육' AND o.status = 'ACTIVE' "
            + "AND (:query = '' OR LOWER(o.title) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "OR LOWER(COALESCE(o.organization, '')) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "ORDER BY o.deadlineAt ASC")
    Page<Opportunity> findActiveTrainingForPromotion(@Param("query") String query, Pageable pageable);
}
