package com.jobpilot.api.domain.resume.repository;

import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeEntryRepository extends JpaRepository<ResumeEntry, Long> {
    List<ResumeEntry> findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(Long memberId);
    Optional<ResumeEntry> findByIdAndMemberId(Long id, Long memberId);
    long deleteByIdAndMemberId(Long id, Long memberId);
    void deleteByMemberId(Long memberId);
}
