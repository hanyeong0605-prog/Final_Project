package com.jobpilot.api.domain.portfolio.repository;

import com.jobpilot.api.domain.portfolio.entity.PortfolioDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioDocumentRepository extends JpaRepository<PortfolioDocument, Long> {
    List<PortfolioDocument> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    // SelfIntroductionRepository.findByIdAndMemberId와 같은 이유의 소유권 체크용.
    Optional<PortfolioDocument> findByIdAndMemberId(Long id, Long memberId);
}
