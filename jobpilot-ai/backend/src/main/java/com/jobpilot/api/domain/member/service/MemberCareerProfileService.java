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
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberCareerProfileService {
    private static final Pattern PHOTO_DATA_URL = Pattern.compile("^data:(image/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$");
    private static final int MAX_PROFILE_PHOTO_BYTES = 2 * 1024 * 1024;
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
        if (request.profilePhotoDataUrl() != null) applyPhoto(spec, request.profilePhotoDataUrl());
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
                s == null ? null : s.getTechnicalSummary(), s == null ? null : s.getPortfolioUrl(), photoDataUrl(s));
    }
    private void applyPhoto(MemberSpecification spec, String dataUrl) {
        if (dataUrl.isBlank()) { spec.updateProfilePhoto(null, null); return; }
        Matcher match = PHOTO_DATA_URL.matcher(dataUrl);
        if (!match.matches()) throw new IllegalArgumentException("사진은 JPG, PNG, WEBP 형식만 첨부할 수 있습니다.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(match.group(2)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("사진 데이터를 읽지 못했습니다."); }
        if (bytes.length > MAX_PROFILE_PHOTO_BYTES) throw new IllegalArgumentException("사진은 2MB 이하만 첨부할 수 있습니다.");
        spec.updateProfilePhoto(bytes, match.group(1));
    }
    private String photoDataUrl(MemberSpecification spec) {
        if (spec == null || spec.getProfilePhoto() == null || spec.getProfilePhotoContentType() == null) return null;
        return "data:" + spec.getProfilePhotoContentType() + ";base64," + Base64.getEncoder().encodeToString(spec.getProfilePhoto());
    }
}
