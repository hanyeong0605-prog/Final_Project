import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router";
import { InterestProvider } from "./features/interests/model/InterestContext";
import { AuthProvider } from "./features/auth/model/AuthContext";
// 2026-08-11: 구글 폰트 CDN(fonts.gstatic.com)에서 우리 환경 기준으로 계속 폰트 파일이
// 404로 실패해서(콘솔 에러) - 원인이 우리 코드가 아니라 외부 네트워크 문제라 고칠 수가
// 없었음. 대신 같은 폰트를 @fontsource로 패키지에 직접 번들해서 외부 요청 자체를 없앴다.
import "@fontsource/dm-mono/400.css";
import "@fontsource/dm-mono/500.css";
import "@fontsource/manrope/400.css";
import "@fontsource/manrope/500.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "@fontsource/manrope/800.css";
import "@fontsource/noto-sans-kr/400.css";
import "@fontsource/noto-sans-kr/500.css";
import "@fontsource/noto-sans-kr/600.css";
import "@fontsource/noto-sans-kr/700.css";
import "@fontsource/noto-sans-kr/800.css";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthProvider><InterestProvider><RouterProvider router={router} /></InterestProvider></AuthProvider>
  </StrictMode>,
);
