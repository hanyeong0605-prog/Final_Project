package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByMemberId(Long memberId);
    // 2026-08-10: 본인 소유가 아닌 프로젝트를 id만 알면 수정/삭제 못 하게 하는 소유권 체크용
    // (SelfIntroductionRepository.findByIdAndMemberId와 같은 패턴).
    Optional<Project> findByIdAndMemberId(Long id, Long memberId);
}
