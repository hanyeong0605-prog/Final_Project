package com.jobpilot.api.domain.auth.repository;

import com.jobpilot.api.domain.auth.entity.OAuthPendingLogin;
import com.jobpilot.api.domain.auth.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthPendingLoginRepository extends JpaRepository<OAuthPendingLogin, String> {
    Optional<OAuthPendingLogin> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
