package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.EducationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EducationHistoryRepository extends JpaRepository<EducationHistory, Long> {
    List<EducationHistory> findByMemberId(Long memberId);
}
