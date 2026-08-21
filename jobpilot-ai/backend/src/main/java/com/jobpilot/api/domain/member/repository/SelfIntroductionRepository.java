package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SelfIntroductionRepository extends JpaRepository<SelfIntroduction, Long> {
    List<SelfIntroduction> findByMemberIdOrderByUpdatedAtDesc(Long memberId);
    // 2026-08-10: 본인 소유가 아닌 글을 id만 알면 수정/삭제할 수 있으면 안 되므로, 수정/삭제
    // 경로에서는 항상 memberId까지 같이 조건에 걸어서 조회한다(PlannerEventRepository의
    // findByIdAndMemberId와 같은 패턴).
    Optional<SelfIntroduction> findByIdAndMemberId(Long id, Long memberId);
    void deleteByMemberId(Long memberId);
}
