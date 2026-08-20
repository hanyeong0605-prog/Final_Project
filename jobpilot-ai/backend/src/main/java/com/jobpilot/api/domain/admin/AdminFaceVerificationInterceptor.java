package com.jobpilot.api.domain.admin;

import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Blocks every sensitive admin API until that same admin has passed face verification. */
@Component
public class AdminFaceVerificationInterceptor implements HandlerInterceptor {
    private static final String FACE_SESSION_HEADER = "X-Admin-Face-Session";
    private final AdminFacePairingService pairings;

    public AdminFaceVerificationInterceptor(AdminFacePairingService pairings) {
        this.pairings = pairings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight has no authentication or face-session header.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        long memberId = AuthenticatedMember.id(authentication);
        String sessionId = request.getHeader(FACE_SESSION_HEADER);
        if (!pairings.isVerified(memberId, sessionId)) {
            throw new AccessDeniedException("관리자 작업을 계속하려면 휴대폰 얼굴 인증을 완료해 주세요.");
        }
        return true;
    }
}
