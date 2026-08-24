import { useEffect, useRef, useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from "../api/notificationApi";

// 2026-08-13: 상단바 종 아이콘 - 원래 onClick이 아예 없던 장식용 버튼이었다. 이미 발송된
// 웹푸시 이력(NotificationLog, /api/v1/notifications)을 그대로 드롭다운으로 보여주는
// 방식으로 구현했다 - 새 "인앱 알림" 개념을 따로 만들지 않고 기존 발송 이력을 재사용.
function formatRelativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;
  return iso.slice(0, 10);
}

export function NotificationBell() {
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void getUnreadCount().then((r) => setUnreadCount(r.count)).catch(() => setUnreadCount(0));
    // 스케줄러가 새 맞춤공고를 기록하면 헤더에서 곧바로 보이도록 15초마다 갱신.
    const interval = setInterval(() => {
      void getUnreadCount().then((r) => setUnreadCount(r.count)).catch(() => {});
    }, 15_000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    listNotifications()
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [open]);

  useEffect(() => {
    const closeOnOutsidePress = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutsidePress);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsidePress);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  const handleItemClick = (item: NotificationItem) => {
    if (!item.read) {
      setItems((prev) => prev.map((it) => (it.id === item.id ? { ...it, read: true } : it)));
      setUnreadCount((count) => Math.max(0, count - 1));
      void markNotificationRead(item.id).catch(() => {});
    }
    setOpen(false);
    if (item.url) navigate(item.url);
  };

  const handleMarkAllRead = () => {
    setItems((prev) => prev.map((it) => ({ ...it, read: true })));
    setUnreadCount(0);
    void markAllNotificationsRead().catch(() => {});
  };

  return (
    <div className="topbar-notification-menu" ref={containerRef}>
      <button
        className="bell"
        aria-label="알림"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell size={19} />
        {unreadCount > 0 && <span />}
      </button>
      {open && (
        <div className="notification-popover" role="menu">
          <div className="notification-popover-head">
            <span>알림</span>
            {unreadCount > 0 && (
              <button type="button" onClick={handleMarkAllRead}><CheckCheck size={13} />모두 읽음</button>
            )}
          </div>
          <div className="notification-popover-list">
            {loading && <p className="notification-empty">불러오는 중...</p>}
            {!loading && items.length === 0 && <p className="notification-empty">아직 알림이 없어요.</p>}
            {!loading && items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`notification-item${item.read ? "" : " unread"}`}
                onClick={() => handleItemClick(item)}
              >
                <span className="notification-item-title">{item.title ?? "알림"}</span>
                {item.body && <span className="notification-item-body">{item.body}</span>}
                <span className="notification-item-time">{formatRelativeTime(item.sentAt)}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
