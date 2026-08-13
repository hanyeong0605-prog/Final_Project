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
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function getJson<T>(path: string): Promise<T> { return requestJson<T>(path); }
export function postJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "POST", body: JSON.stringify(body) });
}

/** Multipart requests must not set Content-Type manually: the browser adds the boundary. */
export async function postForm<T>(path: string, body: FormData): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers({ Accept: "application/json" });
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBaseUrl}${path}`, { method: "POST", body, headers });
  if (!response.ok) {
    if (response.status === 401) clearAccessToken();
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? `POST ${path} failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
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
