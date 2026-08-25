package com.jobpilot.api.domain.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jobpilot.api.global.security.OAuthReturnToRequestFilter;

@Component
public class OAuthAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationSuccessHandler.class);
    private final OAuthLoginService service;
    private final String successRedirect;
    public OAuthAuthenticationSuccessHandler(OAuthLoginService service, @Value("${app.oauth.success-redirect}") String successRedirect) {
        this.service = service; this.successRedirect = successRedirect;
    }
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String target;
        String returnTo = OAuthReturnToRequestFilter.consume(request);
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuth2User user = (OAuth2User) token.getPrincipal();
            OAuthLoginService.LoginResult result = service.begin(token.getAuthorizedClientRegistrationId(), user.getAttributes());
            target = result.isCompleted()
                    ? successRedirect + "#access_token=" + encode(result.response().accessToken()) + returnToFragment(returnTo)
                    : successRedirect.replace("/oauth/callback", "/oauth/complete") + "?ticket=" + encode(result.ticket())
                        + "&provider=" + encode(result.provider()) + "&nickname=" + encode(result.nickname())
                        + (result.email() == null ? "" : "&email=" + encode(result.email()))
                        + returnToQuery(returnTo);
        } catch (RuntimeException error) {
            log.error("OAuth login completion failed", error);
            target = successRedirect.replace("/oauth/callback", "/login") + "?socialError=SOCIAL_LOGIN_FAILED";
        }
        getRedirectStrategy().sendRedirect(request, response, target);
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String returnToFragment(String returnTo) { return returnTo == null ? "" : "&returnTo=" + encode(returnTo); }
    private String returnToQuery(String returnTo) { return returnTo == null ? "" : "&returnTo=" + encode(returnTo); }
}
