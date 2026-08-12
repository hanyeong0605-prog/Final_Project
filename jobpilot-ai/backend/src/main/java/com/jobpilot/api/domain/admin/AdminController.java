package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberRole;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminAccessService adminAccess;
    private final MemberRepository members;
    private final JobPostingRepository postings;

    public AdminController(AdminAccessService adminAccess, MemberRepository members, JobPostingRepository postings) {
        this.adminAccess = adminAccess;
        this.members = members;
        this.postings = postings;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(Authentication authentication) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        return new OverviewResponse(
                members.count(), members.countByRole(MemberRole.ADMIN),
                postings.count(), postings.countByStatus("ACTIVE"), postings.countByStatus("CLOSED"));
    }

    @GetMapping("/members")
    public PageResponse<MemberResponse> members(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Member> result = query.isBlank()
                ? members.findAll(pageable)
                : members.findByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(query, query, query, pageable);
        return PageResponse.from(result.map(MemberResponse::from));
    }

    @PatchMapping("/members/{memberId}/role")
    public MemberResponse changeMemberRole(Authentication authentication, @PathVariable Long memberId,
                                            @Valid @RequestBody ChangeRoleRequest request) {
        Member actor = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Member target = members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
        if (actor.getId().equals(target.getId()) && request.role() != MemberRole.ADMIN) {
            throw new IllegalArgumentException("현재 로그인한 관리자는 관리자 권한을 해제할 수 없습니다.");
        }
        target.changeRole(request.role());
        return MemberResponse.from(target);
    }

    @GetMapping("/job-postings")
    public PageResponse<JobPostingSummary> jobPostings(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "fetchedAt"));
        Page<JobPosting> result = query.isBlank()
                ? postings.findAll(pageable)
                : postings.findByTitleContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(query, query, pageable);
        return PageResponse.from(result.map(JobPostingSummary::from));
    }

    @PatchMapping("/job-postings/{jobPostingId}/status")
    public JobPostingSummary changePostingStatus(Authentication authentication, @PathVariable Long jobPostingId,
                                                   @Valid @RequestBody ChangePostingStatusRequest request) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        if (!request.status().matches("ACTIVE|CLOSED|HIDDEN")) throw new IllegalArgumentException("지원하지 않는 공고 상태입니다.");
        JobPosting posting = postings.findById(jobPostingId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        posting.changeStatus(request.status());
        return JobPostingSummary.from(posting);
    }

    public record OverviewResponse(long memberCount, long adminCount, long jobPostingCount, long activePostingCount, long closedPostingCount) {}
    public record ChangeRoleRequest(@NotNull MemberRole role) {}
    public record ChangePostingStatusRequest(@NotNull String status) {}
    public record JobPostingSummary(Long id, String title, String companyName, String status, String location, LocalDateTime deadlineAt, long viewCount) {
        static JobPostingSummary from(JobPosting posting) {
            return new JobPostingSummary(posting.getId(), posting.getTitle(), posting.getCompanyName(), posting.getStatus(), posting.getLocation(), posting.getDeadlineAt(), posting.getViewCount());
        }
    }
    public record PageResponse<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
