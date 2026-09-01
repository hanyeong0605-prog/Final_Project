package com.jobpilot.api.domain.homepromotion.repository;

import com.jobpilot.api.domain.homepromotion.entity.HomePromotion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePromotionRepository extends JpaRepository<HomePromotion, Long> {
    long countBySlotType(String slotType);
    boolean existsBySlotTypeAndSourceKey(String slotType, String sourceKey);
    List<HomePromotion> findAllByOrderBySlotTypeAscCreatedAtDesc();
}
