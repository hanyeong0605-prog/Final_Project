package com.jobpilot.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The phone QR pages send an unauthenticated user to /login?returnTo=... .
 * OAuth leaves the SPA and returns later, so this small allow-listed value has
 * to survive in the authorization session. Never accept an arbitrary URL here;
 * otherwise the OAuth flow would become an open redirect.
 */
@Component
public class OAuthReturnToRequestFilter extends OncePerRequestFilter {
    private static final String SESSION_ATTRIBUTE = OAuthReturnToRequestFilter.class.getName() + ".returnTo";
    private static final AntPathRequestMatcher AUTHORIZATION_REQUEST =
            new AntPathRequestMatcher("/oauth2/authorization/**");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTHORIZATION_REQUEST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        String returnTo = sanitize(request.getParameter("returnTo"));
        if (returnTo == null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        } else {
            session.setAttribute(SESSION_ATTRIBUTE, returnTo);
        }
        filterChain.doFilter(request, response);
    }

    public static String consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return value instanceof String text ? sanitize(text) : null;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.startsWith("/camera-pair?") || value.startsWith("/admin-face-pair?") ? value : null;
    }
}
