package com.jobpilot.api.domain.member.repository;

import com.jobpilot.api.domain.member.entity.SkillAlias;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, Long> {
    List<SkillAlias> findTop50ByNormalizedAliasStartingWith(String normalizedAlias);
    List<SkillAlias> findByNormalizedAlias(String normalizedAlias);
}
