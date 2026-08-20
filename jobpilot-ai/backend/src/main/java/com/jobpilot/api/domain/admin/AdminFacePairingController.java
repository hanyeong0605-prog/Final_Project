package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/face-pairings")
public class AdminFacePairingController {
    private final AdminAccessService adminAccess;
    private final AdminFacePairingService pairings;
    private final AdminFaceVerificationClient faceVerification;

    public AdminFacePairingController(AdminAccessService adminAccess, AdminFacePairingService pairings, AdminFaceVerificationClient faceVerification) {
        this.adminAccess = adminAccess;
        this.pairings = pairings;
        this.faceVerification = faceVerification;
    }

    @PostMapping
    public CreatedResponse create(Authentication authentication) {
        Member member = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        AdminFacePairingService.Created created = pairings.create(member.getId());
        return new CreatedResponse(created.sessionId(), created.token(), created.expiresAt());
    }

    @PostMapping("/{sessionId}/capture")
    public ResultResponse capture(Authentication authentication, @PathVariable String sessionId, @Valid @RequestBody CaptureRequest request) {
        Member member = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        pairings.verifyOwner(member.getId(), sessionId, request.token());
        AdminFaceVerificationClient.Verification verification = faceVerification.verify(member.getLoginId(), request.imageBase64());
        pairings.complete(member.getId(), sessionId, verification.verified(), verification.similarity(), verification.message());
        return new ResultResponse(verification.verified() ? AdminFacePairingService.Status.VERIFIED : AdminFacePairingService.Status.REJECTED,
                verification.similarity(), verification.message(), null);
    }

    @GetMapping("/{sessionId}")
    public ResultResponse result(Authentication authentication, @PathVariable String sessionId) {
        Member member = adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        AdminFacePairingService.Result result = pairings.result(member.getId(), sessionId);
        return new ResultResponse(result.status(), result.similarity(), result.message(), result.expiresAt());
    }

    public record CreatedResponse(String sessionId, String token, Instant expiresAt) {}
    public record CaptureRequest(@NotBlank String token, @NotBlank String imageBase64) {}
    public record ResultResponse(AdminFacePairingService.Status status, Double similarity, String message, Instant expiresAt) {}
}
