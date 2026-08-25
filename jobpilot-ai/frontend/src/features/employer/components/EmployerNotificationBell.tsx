import { Bell } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getEmployerUnreadCount, listEmployerNotifications, readEmployerNotification, type EmployerNotificationItem } from "../api/employerNotificationApi";

export function EmployerNotificationBell() {
  const navigate = useNavigate(); const ref = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false); const [items, setItems] = useState<EmployerNotificationItem[]>([]); const [count, setCount] = useState(0);
  useEffect(() => { const refresh = () => void getEmployerUnreadCount().then((v) => setCount(v.count)).catch(() => {}); refresh(); const timer = setInterval(refresh, 15000); return () => clearInterval(timer); }, []);
  useEffect(() => { if (open) void listEmployerNotifications().then(setItems).catch(() => setItems([])); }, [open]);
  useEffect(() => { const close = (event: MouseEvent) => { if (!ref.current?.contains(event.target as Node)) setOpen(false); }; document.addEventListener("mousedown", close); return () => document.removeEventListener("mousedown", close); }, []);
  const select = (item: EmployerNotificationItem) => { if (!item.read) { void readEmployerNotification(item.id); setCount((v) => Math.max(0, v - 1)); setItems((all) => all.map((v) => v.id === item.id ? { ...v, read: true } : v)); } setOpen(false); navigate(item.url); };
  return <div className="topbar-notification-menu" ref={ref}><button className="employer-notification-button" aria-label="기업 알림" aria-expanded={open} onClick={() => setOpen((v) => !v)}><Bell size={19} />{count > 0 && <span className="employer-notification-dot">{count > 9 ? "9+" : count}</span>}</button>{open && <div className="notification-popover"><div className="notification-popover-head"><span>기업 알림</span></div><div className="notification-popover-list">{items.length === 0 && <p className="notification-empty">아직 알림이 없어요.</p>}{items.map((item) => <button key={item.id} className={`notification-item-main${item.read ? "" : " unread"}`} onClick={() => select(item)}><span className="notification-item-title">{item.title}</span><span className="notification-item-body">{item.body}</span><span className="notification-item-time">{new Date(item.sentAt).toLocaleString("ko-KR")}</span></button>)}</div></div>}</div>;
}
