package com.jobpilot.api.domain.interview.pairing;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class CameraPairingWebSocketConfig implements WebSocketConfigurer {
    private final CameraPairingWebSocketHandler handler;
    private final CameraPairingWebSocketTicketInterceptor ticketInterceptor;
    private final String[] allowedOrigins;

    public CameraPairingWebSocketConfig(
            CameraPairingWebSocketHandler handler,
            CameraPairingWebSocketTicketInterceptor ticketInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        this.handler = handler;
        this.ticketInterceptor = ticketInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/camera-pair")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }

    // 2026-08-13: "페어링 WebSocket이 종료되었습니다. 코드: 1009 (... too big for the output
    // buffer ...)" 버그 원인 - 내장 톰캣의 WebSocket 컨테이너는 기본 텍스트 메시지 버퍼가
    // 8KB(WsWebSocketContainer 기본값)라, 이 값을 넘는 SDP offer(ICE candidate/코덱이 많으면
    // 특히 큼)가 오면 CameraPairingWebSocketHandler의 자체 64KB 검사(MAX_SIGNAL_BYTES)까지
    // 가보지도 못하고 톰캣 레벨에서 먼저 연결을 끊어버렸다 - 그래서 우리가 만든 친절한
    // 한국어 종료 사유("시그널 메시지가 너무 큽니다.")가 아니라 톰캣의 영어 원문 메시지가
    // 그대로 노출됐다. 톰캣 버퍼를 핸들러의 자체 한도(64KB)보다 넉넉하게(128KB) 키워서,
    // 정상 크기 메시지는 톰캣을 통과하고 진짜 비정상적으로 큰 메시지만 핸들러가 처리하게 한다.
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(128 * 1024);
        container.setMaxBinaryMessageBufferSize(128 * 1024);
        return container;
    }
}
