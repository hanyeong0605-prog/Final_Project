package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.InterviewSessionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InterviewSessionRecordRepository extends JpaRepository<InterviewSessionRecord, Long> {
    List<InterviewSessionRecord> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    // SelfIntroductionRepository.findByIdAndMemberId와 같은 이유의 소유권 체크용(상세 조회에서만 씀 -
    // 이 엔티티는 수정/삭제가 없어서 그 두 경로에서는 안 쓰인다).
    Optional<InterviewSessionRecord> findByIdAndMemberId(Long id, Long memberId);
}
