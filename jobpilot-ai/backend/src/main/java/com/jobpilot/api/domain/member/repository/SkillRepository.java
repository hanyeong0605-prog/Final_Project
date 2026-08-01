package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Skill;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByName(String name);
}
