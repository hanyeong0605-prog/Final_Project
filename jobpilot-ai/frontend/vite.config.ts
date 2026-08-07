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
      '/openapi': {
        target: 'http://openapi.q-net.or.kr',
        changeOrigin: true,
        // /openapi로 시작하는 요청을 Q-Net의 실제 서비스 경로로 통째로 바꿔줍니다.
        rewrite: (path) => path.replace(/^\/openapi/, '/api/service/rest/InquiryListNationalQualifcationSVC/getList'),
      }
    },
  },
});
