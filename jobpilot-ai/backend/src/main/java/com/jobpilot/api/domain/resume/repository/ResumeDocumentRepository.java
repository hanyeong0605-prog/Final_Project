package com.jobpilot.api.domain.resume.repository;
import com.jobpilot.api.domain.resume.entity.ResumeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResumeDocumentRepository extends JpaRepository<ResumeDocument, Long> {
    List<ResumeDocument> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    Optional<ResumeDocument> findByIdAndMemberId(Long id, Long memberId);
    long deleteByIdAndMemberId(Long id, Long memberId);
}
