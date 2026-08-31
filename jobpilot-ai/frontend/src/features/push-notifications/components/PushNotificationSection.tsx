import { useEffect, useState } from "react";
import { getVapidPublicKey, subscribePush, testSendPush, unsubscribePush } from "../api/pushApi";
import { useAuth } from "../../auth/model/AuthContext";

// 2026-08-13: 브라우저 Push API의 applicationServerKey는 Uint8Array를 요구하는데, 서버에서
// 받은 VAPID 공개키는 base64url(URL-safe, 패딩 없음) 문자열이라 표준 atob() 전에 이 변환이
// 필요하다 (web-push 공식 문서/예제에서 쓰는 관용적인 변환 함수).
function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  // Uint8Array.from(...)은 TS lib.dom 최신 타입에서 Uint8Array<ArrayBufferLike>로 추론돼
  // PushSubscriptionOptionsInit.applicationServerKey(BufferSource)에 바로 대입이 안 된다 -
  // new Uint8Array(length) + 루프로 만들면 ArrayBuffer 백킹이 명확해져 타입이 맞는다.
  const output = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) output[i] = rawData.charCodeAt(i);
  return output;
}

// 2026-08-31: 실제 스케줄러 3종이 보내는 문구를 그대로 채워 넣는 프리셋. 실기기 확인이나
// 발표 시연에서 "테스트 알림"이 아니라 진짜 알림 모습을 보여줘야 할 때 쓴다 - 각 문구는
// DeadlineReminderScheduler / RecommendedJobPushScheduler / InterviewPassNoticeScheduler가
// 조립하는 형식과 같게 맞춰뒀다.
const TEST_PRESETS = [
  { label: "마감임박", title: "마감 1일 전이에요", body: "카카오 · 백엔드 개발자 신입 채용", url: "/job-postings/1" },
  { label: "맞춤공고", title: "지금 바로 지원 가능한 새 공고예요", body: "네이버 · 프론트엔드 개발자", url: "/job-postings/1" },
  { label: "무료 이용권", title: "이번 달 무료 이용권이 도착했어요", body: "지금 실전면접을 3회 이용할 수 있어요.", url: "/mock-interview" },
] as const;

// 2026-08-13: 마이페이지에 넣는 "마감임박 알림" 토글 섹션. VAPID 공개키가 비어있으면
// (서버에 키가 설정 안 된 환경 - application.yml push.vapid.* 참고) 아예 렌더링하지 않는다 -
// 눌러도 안 되는 버튼을 보여주는 것보다 숨기는 게 낫다는 기존 TTS/구독 기능의 fail-open
// 관례를 그대로 따른다.
export function PushNotificationSection() {
  const { member } = useAuth();
  const [supported, setSupported] = useState(true);
  const [vapidPublicKey, setVapidPublicKey] = useState("");
  const [subscribed, setSubscribed] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState("");
  const [testMessage, setTestMessage] = useState("");
  // 관리자 테스트 발송용 입력값 - 비워두면 서버가 기본 문구로 보낸다.
  const [testTitle, setTestTitle] = useState("");
  const [testBody, setTestBody] = useState("");
  const [testUrl, setTestUrl] = useState("");

  useEffect(() => {
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) {
      setSupported(false);
      return;
    }
    void getVapidPublicKey().then(({ publicKey }) => setVapidPublicKey(publicKey)).catch(() => setVapidPublicKey(""));
    void navigator.serviceWorker.ready
      .then((registration) => registration.pushManager.getSubscription())
      .then((subscription) => setSubscribed(!!subscription))
      .catch(() => setSubscribed(false));
  }, []);

  const handleSubscribe = async () => {
    setError("");
    setIsBusy(true);
    try {
      // 2026-08-13: iOS 사파리(WebKit)는 pushManager.subscribe()가 알림 권한을 암묵적으로
      // 요청해주는 걸 크롬만큼 안정적으로 처리하지 않는다 - Notification.requestPermission()을
      // 사용자 클릭 핸들러 안에서 명시적으로 먼저 호출해야 iOS에서도 프롬프트가 확실히 뜬다.
      if ("Notification" in window && Notification.permission === "default") {
        const permission = await Notification.requestPermission();
        if (permission !== "granted") {
          throw new DOMException("알림 권한이 거부되었습니다.", "NotAllowedError");
        }
      } else if ("Notification" in window && Notification.permission === "denied") {
        throw new DOMException("알림 권한이 차단되어 있습니다.", "NotAllowedError");
      }
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      });
      const json = subscription.toJSON();
      if (!json.endpoint || !json.keys?.p256dh || !json.keys?.auth) {
        throw new Error("알림 등록 정보가 올바르지 않습니다.");
      }
      await subscribePush({ endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth });
      setSubscribed(true);
    } catch (e) {
      // 2026-08-13: iOS 등 원격 기기에서는 개발자도구로 실제 에러를 확인하기 어려워서,
      // 사용자에게 보이는 메시지에 원인(e.name/e.message)을 그대로 덧붙여 노출한다 -
      // 디버깅 편의를 위한 임시 조치. 문제 해결되면 다시 일반 문구로 되돌려도 된다.
      const detail = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
      setError(
        e instanceof Error && e.name === "NotAllowedError"
          ? "브라우저 알림 권한이 차단되어 있습니다. 브라우저 설정에서 알림을 허용해주세요."
          : `알림 켜기에 실패했습니다. 아이폰이라면 이 사이트를 홈 화면에 추가한 뒤 다시 시도해주세요. (${detail})`,
      );
    } finally {
      setIsBusy(false);
    }
  };

  const handleUnsubscribe = async () => {
    setError("");
    setIsBusy(true);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        await unsubscribePush(subscription.endpoint);
        await subscription.unsubscribe();
      }
      setSubscribed(false);
    } catch (e) {
      const detail = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
      setError(`알림 끄기에 실패했습니다. 잠시 후 다시 시도해주세요. (${detail})`);
    } finally {
      setIsBusy(false);
    }
  };

  const handleTestSend = async () => {
    setTestMessage("");
    setIsBusy(true);
    try {
      const result = await testSendPush({ title: testTitle, body: testBody, url: testUrl });
      setTestMessage(result.sent ? "테스트 알림을 보냈어요. 잠시 후 폰으로 도착하는지 확인해보세요." : (result.reason ?? "발송하지 못했습니다."));
    } catch {
      setTestMessage("테스트 발송에 실패했습니다.");
    } finally {
      setIsBusy(false);
    }
  };

  if (!supported || !vapidPublicKey) return null;

  return (
    <section className="panel push-notification-section">
      <div className="panel-title">
        <div>
          <h2>채용공고 알림</h2>
          <p>찜한 공고 마감이 1일·3일 앞으로 다가오거나, 지금 바로 지원 가능한 맞춤 공고가 뜨면 브라우저 알림으로 알려드려요.</p>
        </div>
      </div>
      {error && <p className="account-alert error">{error}</p>}
      {subscribed ? (
        <button className="outline-button" disabled={isBusy} onClick={() => void handleUnsubscribe()}>
          알림 끄기
        </button>
      ) : (
        <button className="primary-button" disabled={isBusy} onClick={() => void handleSubscribe()}>
          알림 켜기
        </button>
      )}
      {/* 2026-08-13: 관리자 전용 - 마감임박/추천 스케줄러(하루 한 번, 조건부 발송)를 기다리지
          않고 실기기(특히 iOS 홈 화면 추가 후 구독) 테스트를 바로 해볼 수 있게 하는 버튼.
          알림을 켠 상태여야 자기 자신에게 보낼 구독이 있다는 뜻이라 subscribed일 때만 보여준다. */}
      {member?.role === "ADMIN" && subscribed && (
        <div className="push-test-send">
          {/* 2026-08-31: 스케줄러가 실제로 보내는 문구를 실기기에서 그대로 재현해보기 위한 입력칸.
              비워두면 서버가 기본 문구("테스트 알림")로 보낸다 - 기존 동작 그대로. */}
          <div className="push-test-fields">
            <input
              type="text"
              maxLength={200}
              value={testTitle}
              onChange={(event) => setTestTitle(event.target.value)}
              placeholder="제목 (비우면 '테스트 알림')"
            />
            <input
              type="text"
              maxLength={500}
              value={testBody}
              onChange={(event) => setTestBody(event.target.value)}
              placeholder="내용 (비우면 '웹푸시가 정상적으로 도착했어요.')"
            />
            <input
              type="text"
              maxLength={300}
              value={testUrl}
              onChange={(event) => setTestUrl(event.target.value)}
              placeholder="이동 경로 (비우면 /account)"
            />
          </div>
          <div className="push-test-presets">
            {TEST_PRESETS.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className="text-button"
                disabled={isBusy}
                onClick={() => {
                  setTestTitle(preset.title);
                  setTestBody(preset.body);
                  setTestUrl(preset.url);
                }}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <button className="text-button" disabled={isBusy} onClick={() => void handleTestSend()}>
            (관리자) 테스트 알림 보내기
          </button>
          {testMessage && <p>{testMessage}</p>}
        </div>
      )}
    </section>
  );
}
