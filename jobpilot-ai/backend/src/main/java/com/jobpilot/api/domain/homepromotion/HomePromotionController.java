package com.jobpilot.api.domain.homepromotion;

import com.jobpilot.api.domain.homepromotion.entity.HomePromotion;
import com.jobpilot.api.domain.homepromotion.repository.HomePromotionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home-promotions")
public class HomePromotionController {
    private final HomePromotionRepository promotions;

    public HomePromotionController(HomePromotionRepository promotions) { this.promotions = promotions; }

    @GetMapping
    public List<Response> list() {
        return promotions.findAllByOrderBySlotTypeAscCreatedAtDesc().stream().map(Response::from).toList();
    }

    public record Response(Long id, String slotType, String title, String provider, String description,
                           String imageUrl, String targetUrl) {
        static Response from(HomePromotion value) {
            return new Response(value.getId(), value.getSlotType(), value.getTitle(), value.getProvider(),
                    value.getDescription(), value.getImageUrl(), value.getTargetUrl());
        }
    }
}
