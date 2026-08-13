package com.jobpilot.api.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

// 프론트 sw.js 등록 후 PushManager.subscribe()가 돌려주는 PushSubscription 객체에서
// endpoint/keys.p256dh/keys.auth 세 값만 뽑아 평평하게(flatten) 보낸다 - pushApi.ts 참고.
public record PushSubscribeRequest(
        @NotBlank String endpoint,
        @NotBlank String p256dh,
        @NotBlank String auth
) {}
