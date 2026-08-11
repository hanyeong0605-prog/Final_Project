package com.jobpilot.api.domain.Qnet;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnetInit {

    private final QnetService qnetService;

    @EventListener(ApplicationReadyEvent.class)
    public void initData() {
        // 서버가 켜지자마자 데이터 동기화 함수 실행 (테스트용)
        qnetService.fetchAndSaveQualifications();
    }
}