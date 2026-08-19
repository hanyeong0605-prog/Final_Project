import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router";
import { InterestProvider } from "./features/interests/model/InterestContext";
import { AuthProvider } from "./features/auth/model/AuthContext";
import { EmployerAuthProvider } from "./features/employer/model/EmployerAuthContext";
// 2026-08-11: 구글 폰트 CDN(fonts.gstatic.com)에서 우리 환경 기준으로 계속 폰트 파일이
// 404로 실패해서(콘솔 에러) - 원인이 우리 코드가 아니라 외부 네트워크 문제라 고칠 수가
// 없었음. 대신 같은 폰트를 @fontsource로 패키지에 직접 번들해서 외부 요청 자체를 없앴다.
// (같은 이유로) main 브랜치 merge로 styles.css :root에 "Pretendard Variable"이 추가됐는데,
// 원래는 jsdelivr CDN @import였던 걸 여기서도 똑같이 @fontsource로 자체 호스팅했다 -
// "Pretendard Variable"은 패키지가 없어 폴백인 "Pretendard"(@fontsource/pretendard)로 로드,
// font-family 스택에 이미 "Pretendard Variable", "Pretendard" 순으로 있어 자동 폴백된다.
import "@fontsource/pretendard/400.css";
import "@fontsource/pretendard/500.css";
import "@fontsource/pretendard/600.css";
import "@fontsource/pretendard/700.css";
import "@fontsource/pretendard/800.css";
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

// 2026-08-13: 웹푸시 알림(마감임박 공고 등)용 서비스워커 등록 - public/sw.js가 push/
// notificationclick 이벤트를 처리한다(PushNotificationSection.tsx가 이 등록이 끝난 뒤
// navigator.serviceWorker.ready로 구독을 시작함). 지원 안 하는 브라우저(구형 등)에서는
// 조용히 건너뛴다 - 다른 기능엔 영향 없음.
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register("/sw.js").catch(() => {
      // 등록 실패해도 앱 전체가 죽으면 안 되므로 조용히 무시 - 알림 기능만 못 쓰게 됨.
    });
  });
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuthProvider><EmployerAuthProvider><InterestProvider><RouterProvider router={router} /></InterestProvider></EmployerAuthProvider></AuthProvider>
  </StrictMode>,
);
