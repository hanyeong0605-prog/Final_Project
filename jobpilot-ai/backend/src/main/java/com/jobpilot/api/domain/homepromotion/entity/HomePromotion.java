package com.jobpilot.api.domain.homepromotion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "home_promotions")
public class HomePromotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "slot_type", nullable = false) private String slotType;
    @Column(name = "source_key", nullable = false) private String sourceKey;
    @Column(nullable = false) private String title;
    private String provider;
    @Column(length = 1500) private String description;
    @Column(name = "image_url", length = 1500) private String imageUrl;
    @Column(name = "target_url", nullable = false, length = 1500) private String targetUrl;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected HomePromotion() {}

    public static HomePromotion create(String slotType, String sourceKey, String title, String provider,
                                       String description, String imageUrl, String targetUrl) {
        HomePromotion value = new HomePromotion();
        value.slotType = slotType; value.sourceKey = sourceKey; value.title = title; value.provider = provider;
        value.description = description; value.imageUrl = imageUrl; value.targetUrl = targetUrl;
        value.createdAt = LocalDateTime.now();
        return value;
    }

    public Long getId() { return id; }
    public String getSlotType() { return slotType; }
    public String getSourceKey() { return sourceKey; }
    public String getTitle() { return title; }
    public String getProvider() { return provider; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getTargetUrl() { return targetUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
