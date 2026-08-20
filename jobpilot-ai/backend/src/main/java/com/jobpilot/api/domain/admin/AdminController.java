package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.analytics.service.MemberDailyVisitService;
import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.entity.EmployerAccountStatus;
import com.jobpilot.api.domain.employer.repository.EmployerAccountRepository;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.entity.MemberRole;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminAccessService adminAccess;
    private final MemberRepository members;
    private final JobPostingRepository postings;
    private final EmployerAccountRepository employers;
    private final MemberDailyVisitService dailyVisits;

    public AdminController(AdminAccessService adminAccess, MemberRepository members, JobPostingRepository postings,
                           EmployerAccountRepository employers, MemberDailyVisitService dailyVisits) {
        this.adminAccess = adminAccess;
        this.members = members;
        this.postings = postings;
        this.employers = employers;
        this.dailyVisits = dailyVisits;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(Authentication authentication) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        MemberDailyVisitService.DailyVisitorSummary visitors = dailyVisits.today();
        return new OverviewResponse(
                members.count(), members.countByRole(MemberRole.ADMIN),
                postings.count(), postings.countByStatus("ACTIVE"), postings.countByStatus("CLOSED"),
                visitors.total(), visitors.users(), visitors.admins(),
                employers.countByStatus(EmployerAccountStatus.PENDING));
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
        // Repository 조회 메서드의 트랜잭션은 여기 전에 종료될 수 있다. 값만 변경하면
        // 응답에는 역할이 바뀐 것처럼 보이지만 DB에는 반영되지 않을 수 있으므로 명시적으로 저장한다.
        Member saved = members.saveAndFlush(target);
        return MemberResponse.from(saved);
    }

    @PatchMapping("/members/bulk-role")
    public BulkUpdateResponse changeMemberRoles(Authentication authentication, @Valid @RequestBody BulkRoleRequest request) {
        Member actor = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        java.util.List<Member> targets = members.findAllById(request.memberIds());
        for (Member target : targets) {
            if (actor.getId().equals(target.getId()) && request.role() != MemberRole.ADMIN) {
                throw new IllegalArgumentException("현재 로그인한 관리자의 관리자 권한은 해제할 수 없습니다.");
            }
            target.changeRole(request.role());
        }
        members.saveAll(targets);
        return new BulkUpdateResponse(targets.size());
    }

    @DeleteMapping("/members/{memberId}")
    public MemberResponse deleteMember(Authentication authentication, @PathVariable Long memberId) {
        Member actor = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Member target = members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
        if (actor.getId().equals(target.getId())) throw new IllegalArgumentException("현재 로그인한 관리자 계정은 삭제할 수 없습니다.");
        MemberResponse response = MemberResponse.from(target);
        members.delete(target);
        members.flush();
        return response;
    }

    @GetMapping("/job-postings")
    public PageResponse<JobPostingSummary> jobPostings(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "deadline_asc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        String normalizedStatus = status == null ? "ALL" : status.toUpperCase();
        if (!normalizedStatus.matches("ALL|ACTIVE|CLOSED|HIDDEN")) throw new IllegalArgumentException("Unsupported posting status.");
        String normalizedSort = sort == null ? "deadline_asc" : sort;
        if (!normalizedSort.matches("deadline_asc|deadline_desc|recent|popular")) throw new IllegalArgumentException("Unsupported posting sort.");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<JobPosting> result = postings.findForAdmin(query == null ? "" : query.trim(), normalizedStatus, normalizedSort, pageable);
        return PageResponse.from(result.map(JobPostingSummary::from));
    }

    @PatchMapping("/job-postings/{jobPostingId}/status")
    public JobPostingSummary changePostingStatus(Authentication authentication, @PathVariable Long jobPostingId,
                                                   @Valid @RequestBody ChangePostingStatusRequest request) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        if (!request.status().matches("ACTIVE|CLOSED|HIDDEN")) throw new IllegalArgumentException("지원하지 않는 공고 상태입니다.");
        JobPosting posting = postings.findById(jobPostingId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        posting.changeStatus(request.status());
        return JobPostingSummary.from(postings.save(posting));
    }

    @PatchMapping("/job-postings/bulk-status")
    public BulkUpdateResponse changePostingStatuses(Authentication authentication, @Valid @RequestBody BulkPostingStatusRequest request) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        if (!request.status().matches("ACTIVE|CLOSED|HIDDEN")) throw new IllegalArgumentException("지원하지 않는 공고 상태입니다.");
        java.util.List<JobPosting> targets = postings.findAllById(request.jobPostingIds());
        targets.forEach(posting -> posting.changeStatus(request.status()));
        postings.saveAll(targets);
        return new BulkUpdateResponse(targets.size());
    }

    @PutMapping("/job-postings/{jobPostingId}")
    public JobPostingSummary updatePosting(Authentication authentication, @PathVariable Long jobPostingId,
                                            @Valid @RequestBody UpdatePostingRequest request) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        if (!request.status().matches("ACTIVE|CLOSED|HIDDEN")) throw new IllegalArgumentException("지원하지 않는 공고 상태입니다.");
        JobPosting posting = postings.findById(jobPostingId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        posting.updateByAdmin(request.title().trim(), blankToNull(request.companyName()), blankToNull(request.location()), request.deadlineAt(), request.status());
        return JobPostingSummary.from(postings.save(posting));
    }

    @DeleteMapping("/job-postings/{jobPostingId}")
    public JobPostingSummary hidePosting(Authentication authentication, @PathVariable Long jobPostingId) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        JobPosting posting = postings.findById(jobPostingId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        posting.changeStatus("HIDDEN");
        return JobPostingSummary.from(postings.save(posting));
    }

    // 2026-08-19: 기업회원 승인 관리 - 목록/승인/거절. 상태(PENDING/APPROVED/REJECTED)
    // 필터는 회원 관리 검색과 동일하게 페이지네이션+검색어 조합으로 처리한다.
    @GetMapping("/employers")
    public PageResponse<EmployerSummary> employers(
            Authentication authentication,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedStatus = status == null ? "ALL" : status.toUpperCase();
        if (!normalizedStatus.matches("ALL|PENDING|APPROVED|REJECTED")) throw new IllegalArgumentException("지원하지 않는 기업회원 상태입니다.");
        String trimmedQuery = query == null ? "" : query.trim();
        Page<EmployerAccount> result = normalizedStatus.equals("ALL")
                ? (trimmedQuery.isBlank()
                        ? employers.findAll(pageable)
                        : employers.findByCompanyNameContainingIgnoreCaseOrManagerNameContainingIgnoreCaseOrBusinessRegistrationNumberContaining(trimmedQuery, trimmedQuery, trimmedQuery, pageable))
                : employers.findByStatus(EmployerAccountStatus.valueOf(normalizedStatus), pageable);
        return PageResponse.from(result.map(EmployerSummary::from));
    }

    @PatchMapping("/employers/{employerId}/approve")
    public EmployerSummary approveEmployer(Authentication authentication, @PathVariable Long employerId) {
        Member actor = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        EmployerAccount employer = employers.findById(employerId).orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다."));
        employer.approve(actor.getId());
        return EmployerSummary.from(employers.save(employer));
    }

    @PatchMapping("/employers/{employerId}/reject")
    public EmployerSummary rejectEmployer(Authentication authentication, @PathVariable Long employerId,
                                           @Valid @RequestBody RejectEmployerRequest request) {
        Member actor = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        EmployerAccount employer = employers.findById(employerId).orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다."));
        employer.reject(actor.getId(), blankToNull(request.reason()));
        return EmployerSummary.from(employers.save(employer));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record OverviewResponse(long memberCount, long adminCount, long jobPostingCount, long activePostingCount, long closedPostingCount,
                                   long todayVisitorCount, long todayUserVisitorCount, long todayAdminVisitorCount,
                                   long employerPendingCount) {}
    public record RejectEmployerRequest(String reason) {}
    public record EmployerSummary(Long id, String companyName, String managerName, String managerPhone, String email,
                                   String businessRegistrationNumber, String representativeName, String openingDate,
                                   boolean ntsVerified, EmployerAccountStatus status, String rejectionReason, LocalDateTime createdAt) {
        static EmployerSummary from(EmployerAccount employer) {
            return new EmployerSummary(employer.getId(), employer.getCompanyName(), employer.getManagerName(), employer.getManagerPhone(),
                    employer.getEmail(), employer.getBusinessRegistrationNumber(), employer.getRepresentativeName(),
                    employer.getOpeningDate(), employer.isNtsVerified(), employer.getStatus(), employer.getRejectionReason(), employer.getCreatedAt());
        }
    }
    public record ChangeRoleRequest(@NotNull MemberRole role) {}
    public record BulkRoleRequest(@NotNull java.util.List<Long> memberIds, @NotNull MemberRole role) {}
    public record ChangePostingStatusRequest(@NotNull String status) {}
    public record BulkPostingStatusRequest(@NotNull java.util.List<Long> jobPostingIds, @NotBlank String status) {}
    public record BulkUpdateResponse(int updatedCount) {}
    public record UpdatePostingRequest(@NotBlank String title, String companyName, String location,
                                       LocalDateTime deadlineAt, @NotBlank String status) {}
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
