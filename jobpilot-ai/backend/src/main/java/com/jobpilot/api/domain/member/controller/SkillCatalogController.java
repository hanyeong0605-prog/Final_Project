package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.SkillCatalogItemResponse;
import com.jobpilot.api.domain.member.service.SkillCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillCatalogController {
    private final SkillCatalogService skillCatalogService;

    public SkillCatalogController(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    @GetMapping
    public List<SkillCatalogItemResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer limit
    ) {
        return skillCatalogService.search(query, category, limit);
    }
}
