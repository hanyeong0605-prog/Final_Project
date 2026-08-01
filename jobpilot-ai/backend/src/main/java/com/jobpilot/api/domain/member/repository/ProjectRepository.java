package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByMemberId(Long memberId);
}
