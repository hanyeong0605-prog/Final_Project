package com.jobpilot.api.domain.auth.repository;

import com.jobpilot.api.domain.auth.entity.MemberOAuthAccount;
import com.jobpilot.api.domain.auth.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberOAuthAccountRepository extends JpaRepository<MemberOAuthAccount, Long> {
    Optional<MemberOAuthAccount> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);
}
