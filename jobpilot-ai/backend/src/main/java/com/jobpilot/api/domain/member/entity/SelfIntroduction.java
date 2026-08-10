package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "self_introductions")
public class SelfIntroduction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private String title;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String content;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected SelfIntroduction() {}
    // 2026-08-10: 이력서 작성 도우미 기능(회원 CRUD) 추가하면서 생성자/수정 메서드를 새로
    // 붙였다 - 엔티티 자체는 이미 있었지만 Service/Controller가 없어서 아무 데서도 안
    // 쓰이고 있었다(레포지토리만 존재).
    public SelfIntroduction(Long memberId, String title, String content, boolean primary) {
        this.memberId = memberId;
        this.title = title;
        this.content = content;
        this.primary = primary;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    public void update(String title, String content, boolean primary) {
        this.title = title;
        this.content = content;
        this.primary = primary;
        this.updatedAt = LocalDateTime.now();
    }
    // 대표 자기소개서는 회원당 하나만 유지한다 - 새로 하나를 대표로 지정할 때 나머지의
    // primary만 따로 끄기 위한 부분 업데이트(전체 update()를 쓰면 그 항목의 title/content도
    // 같이 덮어써야 해서 번거롭다).
    public void unsetPrimary() {
        this.primary = false;
        this.updatedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPrimary() { return primary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
