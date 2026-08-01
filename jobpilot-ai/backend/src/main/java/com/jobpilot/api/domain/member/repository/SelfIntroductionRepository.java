package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SelfIntroductionRepository extends JpaRepository<SelfIntroduction, Long> {
    List<SelfIntroduction> findByMemberIdOrderByUpdatedAtDesc(Long memberId);
}
