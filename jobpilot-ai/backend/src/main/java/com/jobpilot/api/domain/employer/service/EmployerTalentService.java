package com.jobpilot.api.domain.employer.service;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberProfile;
import com.jobpilot.api.domain.member.entity.MemberSpecification;
import com.jobpilot.api.domain.member.repository.MemberProfileRepository;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.member.repository.MemberSkillRepository;
import com.jobpilot.api.domain.member.repository.MemberSpecificationRepository;
import com.jobpilot.api.domain.member.repository.SkillRepository;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 기업은 공개를 선택한 회원만 이 서비스로 조회한다. 즐겨찾기도 조회 시점에 다시 공개 여부를 확인한다. */
@Service
@Transactional
public class EmployerTalentService {
    private final EmployerAccessService employers; private final MemberRepository members; private final MemberProfileRepository profiles;
    private final MemberSpecificationRepository specifications; private final MemberSkillRepository memberSkills; private final SkillRepository skills;
    private final NotificationLogRepository notifications; private final JdbcTemplate jdbc;
    public EmployerTalentService(EmployerAccessService employers, MemberRepository members, MemberProfileRepository profiles,
            MemberSpecificationRepository specifications, MemberSkillRepository memberSkills, SkillRepository skills,
            NotificationLogRepository notifications, JdbcTemplate jdbc) {
        this.employers = employers; this.members = members; this.profiles = profiles; this.specifications = specifications;
        this.memberSkills = memberSkills; this.skills = skills; this.notifications = notifications; this.jdbc = jdbc;
    }
    public boolean visibility(Long memberId) { return profiles.findById(memberId).map(MemberProfile::isTalentPublic).orElse(false); }
    public boolean changeVisibility(Long memberId, boolean enabled) {
        MemberProfile profile = profiles.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("먼저 스펙정보를 저장해 주세요."));
        profile.changeTalentPublic(enabled); profiles.save(profile); return enabled;
    }
    public List<Talent> list(Long employerId, String query) {
        employers.requireApproved(employerId); String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return profiles.findAll().stream().filter(MemberProfile::isTalentPublic).map(this::summary)
                .filter(item -> needle.isBlank() || item.searchText().contains(needle)).toList();
    }
    public Talent detail(Long employerId, Long memberId) {
        employers.requireApproved(employerId); MemberProfile profile = publicProfile(memberId); Talent item = summary(profile);
        String company = employers.requireApproved(employerId).getCompanyName();
        // 동일 기업이 이미 조회한 프로필은 기존 알림 이력을 재사용한다. notification_logs의
        // 중복 방지 키에 걸려 상세 화면 자체가 500이 되는 것을 막고, 종 알림도 불필요하게
        // 같은 내용으로 쌓이지 않게 한다.
        if (!notifications.existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(memberId, "EMPLOYER_PROFILE", employerId, "EMPLOYER_PROFILE_VIEW")) {
            notifications.save(new NotificationLog(memberId, "EMPLOYER_PROFILE", employerId, "EMPLOYER_PROFILE_VIEW",
                    "기업회원이 내 스펙을 조회했어요", company + "에서 공개한 역량 정보를 조회했습니다.", "/account"));
        }
        return item;
    }
    public boolean toggleFavorite(Long employerId, Long memberId) {
        employers.requireApproved(employerId); publicProfile(memberId);
        Integer count = jdbc.queryForObject("select count(*) from employer_talent_favorites where employer_account_id=? and member_id=?", Integer.class, employerId, memberId);
        if (count != null && count > 0) { jdbc.update("delete from employer_talent_favorites where employer_account_id=? and member_id=?", employerId, memberId); return false; }
        jdbc.update("insert into employer_talent_favorites(employer_account_id, member_id) values (?, ?)", employerId, memberId); return true;
    }
    public List<Talent> favorites(Long employerId) {
        employers.requireApproved(employerId);
        return jdbc.queryForList("select member_id from employer_talent_favorites where employer_account_id=? order by created_at desc", Long.class, employerId).stream()
                .map(profiles::findById).flatMap(java.util.Optional::stream).filter(MemberProfile::isTalentPublic).map(this::summary).toList();
    }
    private MemberProfile publicProfile(Long memberId) { return profiles.findById(memberId).filter(MemberProfile::isTalentPublic).orElseThrow(() -> new ResourceNotFoundException("공개 중인 인재 정보를 찾을 수 없습니다.")); }
    private Talent summary(MemberProfile profile) {
        Long id = profile.getMemberId(); Member member = members.findById(id).orElseThrow(); MemberSpecification spec = specifications.findById(id).orElse(null);
        List<String> skillNames = memberSkills.findByMemberId(id).stream().map(s -> skills.findById(s.getSkillId()).map(v -> v.getName()).orElse(null)).filter(java.util.Objects::nonNull).toList();
        String location = profile.getPreferredLocations() == null ? "" : profile.getPreferredLocations().toString().replaceAll("[\\[\\]\"]", "").replace(',', ' ');
        String role = blankLabel(profile.getTargetRole(), "희망 직무 미설정");
        String family = blankLabel(profile.getTargetJobFamily(), "직무 분야 미설정");
        location = blankLabel(location, "희망 지역 미설정");
        String text = (member.getNickname()+" "+role+" "+family+" "+location+" "+String.join(" ", skillNames)).toLowerCase(Locale.ROOT);
        return new Talent(id, member.getNickname(), role, family, location, blankLabel(profile.getExperienceType(), "경력 정보 미설정"),
                spec == null ? 0 : spec.getTotalCareerMonths(), spec == null ? null : spec.getTechnicalSummary(), spec == null ? null : spec.getPortfolioUrl(), skillNames, text);
    }
    private String blankLabel(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    public record Talent(Long memberId, String nickname, String targetRole, String targetJobFamily, String preferredLocations,
            String experienceType, int totalCareerMonths, String technicalSummary, String portfolioUrl, List<String> skills, String searchText) {}
}
