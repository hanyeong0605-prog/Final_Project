package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByName(String name);
    Optional<Skill> findFirstByCatalogStatusAndNormalizedName(String catalogStatus, String normalizedName);
    List<Skill> findTop50ByCatalogStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscNameAsc(String catalogStatus, String name);
    List<Skill> findTop50ByCatalogStatusAndCategoryOrderByDisplayOrderAscNameAsc(String catalogStatus, String category);
    List<Skill> findTop50ByCatalogStatusOrderByDisplayOrderAscNameAsc(String catalogStatus);
}
