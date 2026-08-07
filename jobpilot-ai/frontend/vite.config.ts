import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  envDir: "..",
  plugins: [react()],
  server: {
    port: 5173,
    // ngrok으로 폰 테스트할 때 Vite가 낯선 Host 헤더를 막는 것(DNS 리바인딩 방지)을
    // 풀어준다. ngrok 무료 주소는 세션마다 바뀌므로 서브도메인 전체를 허용해둔다.
    allowedHosts: [".ngrok-free.dev", ".ngrok-free.app", ".ngrok.app"],
    // 모의면접 페이지가 파이썬 ai-server(8001)로 보내는 요청을 이 dev 서버가 대신
    // 전달해준다. 그러면 폰에서 ngrok으로 접속할 때도 터널 하나(프론트용)만 있으면
    // 되고, 브라우저 입장에서는 같은 출처(origin)로 보이니 CORS 문제도 없어진다.
    proxy: {
      "/ai-api": {
        target: "http://localhost:8001",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ai-api/, ""),
      },
      // 스프링 백엔드(로그인 등)도 같은 이유로 프록시한다 - 폰에서 ngrok으로 접속하면
      // "localhost:9000"은 폰 자신을 가리켜서 실패하기 때문.
      "/api": {
        target: "http://localhost:9000",
        changeOrigin: true,
      },
      // 2026-08-07: 폰 카메라 페어링(CameraPairingWebSocketConfig, 스프링 백엔드 9000번의
      // /ws/camera-pair)용 프록시가 빠져 있었다 - cameraPairing.ts의 pairingWebSocketUrl()이
      // window.location.host(=Vite 개발서버 5173)로 접속을 시도하는데, Vite는 이 경로를
      // 모르니 백엔드까지 연결이 안 닿아서 PC 쪽이 peer-ready 신호를 못 받고, 결국 offer를
      // 못 보내 폰 카메라는 뜨는데 PC 화면엔 아무것도 안 나오는 증상으로 이어졌다.
      // ws: true가 있어야 HTTP 프록시가 아니라 WebSocket 업그레이드를 그대로 전달한다.
      "/ws": {
        target: "http://localhost:9000",
        changeOrigin: true,
        ws: true,
      },
    },
  },
});
