package com.jobpilot.api.domain.notification.repository;

import com.jobpilot.api.domain.notification.entity.NotificationLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByMemberIdAndTargetTypeAndTargetIdAndNotificationType(
            Long memberId, String targetType, Long targetId, String notificationType);

    // 종 아이콘 드롭다운 - 최근 것부터, 너무 길어지지 않게 화면에서 List.subList로 잘라 쓴다
    // (Pageable을 쓸 정도로 무거운 화면은 아니라서 단순하게 유지).
    List<NotificationLog> findTop30ByMemberIdOrderBySentAtDesc(Long memberId);

    long countByMemberIdAndReadFalse(Long memberId);

    Optional<NotificationLog> findByIdAndMemberId(Long id, Long memberId);

    @Modifying
    @Query("update NotificationLog n set n.read = true where n.memberId = :memberId and n.read = false")
    int markAllReadByMemberId(@Param("memberId") Long memberId);
}
