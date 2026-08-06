package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.SkillCatalogItemResponse;
import com.jobpilot.api.domain.member.entity.Skill;
import com.jobpilot.api.domain.member.entity.SkillAlias;
import com.jobpilot.api.domain.member.repository.SkillAliasRepository;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SkillCatalogService {
    private static final String CANONICAL = "CANONICAL";
    private static final int DEFAULT_LIMIT = 20;

    private final SkillRepository skills;
    private final SkillAliasRepository aliases;

    public SkillCatalogService(SkillRepository skills, SkillAliasRepository aliases) {
        this.skills = skills;
        this.aliases = aliases;
    }

    public List<SkillCatalogItemResponse> search(String query, String category, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, 50);
        String trimmedQuery = query == null ? "" : query.trim();
        String normalizedQuery = normalize(trimmedQuery);
        String normalizedCategory = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);

        LinkedHashMap<Long, Skill> candidates = new LinkedHashMap<>();
        if (trimmedQuery.isBlank()) {
            List<Skill> listed = normalizedCategory.isBlank()
                    ? skills.findTop50ByCatalogStatusOrderByDisplayOrderAscNameAsc(CANONICAL)
                    : skills.findTop50ByCatalogStatusAndCategoryOrderByDisplayOrderAscNameAsc(CANONICAL, normalizedCategory);
            listed.forEach(skill -> candidates.put(skill.getId(), skill));
        } else {
            skills.findTop50ByCatalogStatusAndNameContainingIgnoreCaseOrderByDisplayOrderAscNameAsc(CANONICAL, trimmedQuery)
                    .forEach(skill -> candidates.put(skill.getId(), skill));
            aliases.findTop50ByNormalizedAliasStartingWith(normalizedQuery).stream()
                    .map(SkillAlias::getSkillId)
                    .map(skills::findById)
                    .flatMap(Optional::stream)
                    .filter(Skill::isCanonical)
                    .forEach(skill -> candidates.put(skill.getId(), skill));
        }

        return candidates.values().stream()
                .filter(skill -> normalizedCategory.isBlank() || normalizedCategory.equals(skill.getCategory()))
                .limit(effectiveLimit)
                .map(skill -> new SkillCatalogItemResponse(
                        skill.getId(), skill.getName(), skill.getCategory(), skill.getParentSkillId()))
                .toList();
    }

    public Optional<Skill> resolveCanonical(String rawName) {
        String normalized = normalize(rawName);
        if (normalized.isBlank()) return Optional.empty();

        Optional<Skill> direct = skills.findFirstByCatalogStatusAndNormalizedName(CANONICAL, normalized);
        if (direct.isPresent()) return direct;

        return aliases.findByNormalizedAlias(normalized).stream()
                .map(SkillAlias::getSkillId)
                .map(skills::findById)
                .flatMap(Optional::stream)
                .filter(Skill::isCanonical)
                .findFirst();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("c#", "csharp")
                .replace("c++", "cplusplus")
                .replace("+", "plus");
        return normalized.replaceAll("[\\s._/()\\-]", "");
    }
}
