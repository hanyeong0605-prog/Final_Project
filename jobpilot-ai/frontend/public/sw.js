// 2026-08-13: 웹푸시 알림용 서비스워커. 백엔드(WebPushService)가 보내는 payload는
// {"title": ..., "body": ..., "url": ...} 형태의 JSON 문자열이다(WebPushService.sendToMember
// 참고) - 여기서 그대로 파싱해서 알림을 띄우고, 클릭하면 url로 이동시킨다.
// vite build가 public/ 아래 파일을 그대로 dist/ 루트로 복사하므로 별도 번들링 설정 없이
// /sw.js 경로로 서비스된다.

self.addEventListener("push", (event) => {
  let data = { title: "Job-A-Dream", body: "새 알림이 있어요", url: "/" };
  if (event.data) {
    try {
      data = { ...data, ...event.data.json() };
    } catch {
      // JSON이 아니면 기본 문구를 그대로 쓴다 - 알림 자체를 안 보여주는 것보다 낫다.
    }
  }
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: "/app-icon.png",
      badge: "/app-icon.png",
      data: { url: data.url },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification.data && event.notification.data.url ? event.notification.data.url : "/";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes(targetUrl) && "focus" in client) return client.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow(targetUrl);
      return undefined;
    }),
  );
});
