package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.MemberSkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberSkillRepository extends JpaRepository<MemberSkill, Long> {
    List<MemberSkill> findByMemberId(Long memberId);
}
