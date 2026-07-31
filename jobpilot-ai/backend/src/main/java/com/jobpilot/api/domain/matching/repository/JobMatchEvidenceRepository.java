package com.jobpilot.api.domain.matching.repository;

import com.jobpilot.api.domain.matching.entity.JobMatchEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobMatchEvidenceRepository extends JpaRepository<JobMatchEvidence, Long> {
    List<JobMatchEvidence> findByJobMatchId(Long jobMatchId);
}
