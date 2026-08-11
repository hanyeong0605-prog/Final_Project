package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    long countByRole(com.jobpilot.api.domain.member.entity.MemberRole role);
    Page<Member> findByLoginIdContainingIgnoreCaseOrEmailContainingIgnoreCaseOrNicknameContainingIgnoreCase(
            String loginId, String email, String nickname, Pageable pageable);
}
