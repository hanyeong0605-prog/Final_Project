package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.MemberSkillRequest;
import com.jobpilot.api.domain.member.dto.MemberSkillResponse;
import com.jobpilot.api.domain.member.entity.MemberSkill;
import com.jobpilot.api.domain.member.entity.Skill;
import com.jobpilot.api.domain.member.repository.MemberSkillRepository;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import com.jobpilot.api.domain.matching.service.JobMatchRefreshScheduler;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberSkillService {
    private static final int MAX_SKILLS = 40;

    private final MemberSkillRepository memberSkills;
    private final SkillRepository skills;
    private final JobMatchRefreshScheduler matchRefreshScheduler;

    public MemberSkillService(MemberSkillRepository memberSkills, SkillRepository skills, JobMatchRefreshScheduler matchRefreshScheduler) {
        this.memberSkills = memberSkills;
        this.skills = skills;
        this.matchRefreshScheduler = matchRefreshScheduler;
    }

    public List<MemberSkillResponse> get(Long memberId) {
        List<MemberSkill> saved = memberSkills.findByMemberId(memberId);
        Map<Long, Skill> catalog = skills.findAllById(saved.stream().map(MemberSkill::getSkillId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Skill::getId, Function.identity()));
        return saved.stream()
                .map(memberSkill -> toResponse(memberSkill, catalog.get(memberSkill.getSkillId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<MemberSkillResponse> replace(Long memberId, List<MemberSkillRequest> requested) {
        List<MemberSkillRequest> input = requested == null ? List.of() : requested;
        if (input.size() > MAX_SKILLS) throw new IllegalArgumentException("보유 기술은 최대 40개까지 저장할 수 있습니다.");

        Set<Long> ids = new HashSet<>();
        for (MemberSkillRequest item : input) {
            if (!ids.add(item.skillId())) throw new IllegalArgumentException("같은 기술을 중복해서 선택할 수 없습니다.");
        }

        Map<Long, Skill> catalog = skills.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Skill::getId, Function.identity()));
        if (catalog.size() != ids.size() || catalog.values().stream().anyMatch(skill -> !skill.isCanonical())) {
            throw new IllegalArgumentException("기술 목록에서 검색해 선택한 항목만 저장할 수 있습니다.");
        }

        memberSkills.deleteByMemberId(memberId);
        memberSkills.flush();
        List<MemberSkill> saved = memberSkills.saveAll(input.stream()
                .map(item -> new MemberSkill(memberId, item.skillId(), "LEARNING", clean(item.note())))
                .toList());
        matchRefreshScheduler.enqueueForMember(memberId);
        return saved.stream().map(memberSkill -> toResponse(memberSkill, catalog.get(memberSkill.getSkillId()))).toList();
    }

    private MemberSkillResponse toResponse(MemberSkill memberSkill, Skill skill) {
        if (skill == null || !skill.isCanonical()) return null;
        return new MemberSkillResponse(skill.getId(), skill.getName(), skill.getCategory(), memberSkill.getSelfReportedLevel(), memberSkill.getNote());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
