package com.jobpilot.api.domain.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.member.dto.MemberCareerProfileRequest;
import com.jobpilot.api.domain.member.dto.MemberCareerProfileResponse;
import com.jobpilot.api.domain.member.entity.*;
import com.jobpilot.api.domain.member.repository.*;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.domain.matching.service.JobMatchRefreshScheduler;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberCareerProfileService {
    private final MemberRepository members; private final MemberProfileRepository profiles;
    private final MemberSpecificationRepository specifications; private final ObjectMapper objectMapper;
    private final JobMatchRefreshScheduler matchRefreshScheduler;
    public MemberCareerProfileService(MemberRepository members, MemberProfileRepository profiles,
            MemberSpecificationRepository specifications, ObjectMapper objectMapper, JobMatchRefreshScheduler matchRefreshScheduler) {
        this.members = members; this.profiles = profiles; this.specifications = specifications; this.objectMapper = objectMapper; this.matchRefreshScheduler = matchRefreshScheduler;
    }
    public MemberCareerProfileResponse get(Long memberId) {
        MemberProfile profile = profiles.findById(memberId).orElse(null);
        MemberSpecification spec = specifications.findById(memberId).orElse(null);
        if (profile == null && spec == null) return null;
        return response(profile, spec);
    }
    public MemberCareerProfileResponse save(Long memberId, MemberCareerProfileRequest request) {
        Member member = member(memberId);
        MemberProfile profile = profiles.findById(memberId).orElseGet(() -> new MemberProfile(memberId));
        ArrayNode locations = objectMapper.createArrayNode();
        if (request.preferredLocations() != null) request.preferredLocations().stream().map(String::trim).filter(v -> !v.isEmpty()).forEach(locations::add);
        profile.update(request.targetRole().trim(), request.targetJobFamily().trim(), locations, request.availableFrom(),
                request.experienceType().trim().toUpperCase(), clean(request.githubUsername()));
        MemberSpecification spec = specifications.findById(memberId).orElseGet(() -> new MemberSpecification(memberId));
        spec.update(clean(request.educationLevel()), clean(request.schoolName()), clean(request.major()),
                clean(request.graduationStatus()), request.totalCareerMonths(), clean(request.technicalSummary()), clean(request.portfolioUrl()));
        profiles.save(profile); specifications.save(spec); member.completeOnboarding();
        matchRefreshScheduler.enqueueForMember(memberId);
        return response(profile, spec);
    }
    public MemberResponse skip(Long memberId) { Member member = member(memberId); member.completeOnboarding(); return MemberResponse.from(member); }
    private Member member(Long id) { return members.findById(id).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다.")); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private MemberCareerProfileResponse response(MemberProfile p, MemberSpecification s) {
        List<String> locations = p == null || p.getPreferredLocations() == null ? List.of() :
                objectMapper.convertValue(p.getPreferredLocations(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        return new MemberCareerProfileResponse(p == null ? "" : p.getTargetRole(), p == null ? "" : p.getTargetJobFamily(), locations,
                p == null ? null : p.getAvailableFrom(), p == null ? "ENTRY" : p.getExperienceType(), p == null ? null : p.getGithubUsername(),
                s == null ? null : s.getEducationLevel(), s == null ? null : s.getSchoolName(), s == null ? null : s.getMajor(),
                s == null ? null : s.getGraduationStatus(), s == null ? 0 : s.getTotalCareerMonths(),
                s == null ? null : s.getTechnicalSummary(), s == null ? null : s.getPortfolioUrl());
    }
}
