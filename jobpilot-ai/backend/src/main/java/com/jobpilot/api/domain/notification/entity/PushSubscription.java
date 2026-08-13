package com.jobpilot.api.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false, length = 1000) private String endpoint;
    @Column(nullable = false) private String p256dh;
    @Column(name = "auth_key", nullable = false) private String authKey;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected PushSubscription() {}

    public PushSubscription(Long memberId, String endpoint, String p256dh, String authKey, String userAgent) {
        this.memberId = memberId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.createdAt = LocalDateTime.now();
    }

    // 같은 브라우저가 재구독(권한을 다시 허용하는 등)했을 때 로그인 계정이 바뀌었을 수
    // 있어서, 키를 통째로 새로 저장하지 않고 기존 행을 그대로 갱신한다 - endpoint가
    // UNIQUE라 이게 사실상의 upsert다 (PushSubscriptionService 참고).
    public void refresh(Long memberId, String p256dh, String authKey, String userAgent) {
        this.memberId = memberId;
        this.p256dh = p256dh;
        this.authKey = authKey;
        this.userAgent = userAgent;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuthKey() { return authKey; }
}
