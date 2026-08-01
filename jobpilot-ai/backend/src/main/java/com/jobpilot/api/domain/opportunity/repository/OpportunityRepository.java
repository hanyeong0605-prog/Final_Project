package com.jobpilot.api.domain.opportunity.repository;

import com.jobpilot.api.domain.opportunity.entity.Opportunity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findByStatusOrderByDeadlineAtAsc(String status);
}
