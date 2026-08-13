// In development Vite proxies /api, and in production Nginx proxies it.
// An explicit VITE_API_BASE_URL remains available for environments that need it.
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const tokenKey = "jobpilot.accessToken";

export function getAccessToken() { return localStorage.getItem(tokenKey); }
export function setAccessToken(token: string) { localStorage.setItem(tokenKey, token); }
export function clearAccessToken() { localStorage.removeItem(tokenKey); }

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  if (!response.ok) {
    if (response.status === 401) clearAccessToken();
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? `${init.method ?? "GET"} ${path} failed: ${response.status}`);
  }
  // 2026-08-13: 백엔드가 void를 리턴하는 엔드포인트(예: 웹푸시 subscribe/unsubscribe)는
  // 204가 아니라 200에 빈 본문으로 응답하는 경우가 있다 - response.json()을 빈 문자열에
  // 그대로 호출하면 "Unexpected end of JSON input"으로 터져서, 실제로는 성공한 요청이
  // 프론트에서는 실패로 보이는 버그가 있었다(웹푸시 알림 끄기 등). 상태코드 대신 실제
  // 본문 텍스트가 비어있는지로 판단해서 204와 동일하게 처리한다.
  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export function getJson<T>(path: string): Promise<T> { return requestJson<T>(path); }
export function postJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "POST", body: JSON.stringify(body) });
}
export function patchJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "PATCH", body: JSON.stringify(body) });
}
export function putJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "PUT", body: JSON.stringify(body) });
}
export function deleteJson(path: string, body?: unknown): Promise<void> {
  return requestJson<void>(path, { method: "DELETE", body: body === undefined ? undefined : JSON.stringify(body) });
}
// deleteJson()은 응답 본문을 버리는 void 전용이라, 삭제 후 서버가 돌려주는 최신 목록이
// 필요한 곳(자격증 찜 해제 등)을 위해 별도로 둔다 - 기존 deleteJson 시그니처는 건드리지 않음.
export function deleteJsonReturning<T>(path: string): Promise<T> {
  return requestJson<T>(path, { method: "DELETE" });
}
