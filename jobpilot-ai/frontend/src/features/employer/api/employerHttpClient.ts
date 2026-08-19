// 2026-08-19: 기업회원 전용 fetch 클라이언트 - 기존 api/httpClient.ts(구직자/관리자 토큰,
// localStorage 키 "jobpilot.accessToken")와 완전히 다른 토큰 저장 키를 쓴다. 같은 키를
// 공유하면 기업회원으로 로그인하는 순간 구직자 세션이 덮어씌워지기 때문에, 별도 키로 두 세션이
// 브라우저 한쪽에서도 서로 안 건드리게 분리했다(백엔드도 JWT의 actorType 클레임으로 서로의
// 전용 API를 못 타게 막아뒀다 - AuthenticatedEmployer/AuthenticatedMember 참고).
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
const tokenKey = "jobpilot.employerAccessToken";

export function getEmployerAccessToken() { return localStorage.getItem(tokenKey); }
export function setEmployerAccessToken(token: string) { localStorage.setItem(tokenKey, token); }
export function clearEmployerAccessToken() { localStorage.removeItem(tokenKey); }

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getEmployerAccessToken();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  if (!response.ok) {
    if (response.status === 401) clearEmployerAccessToken();
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? `${init.method ?? "GET"} ${path} failed: ${response.status}`);
  }
  const text = await response.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export function getEmployerJson<T>(path: string, init: RequestInit = {}): Promise<T> { return requestJson<T>(path, init); }
export function postEmployerJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "POST", body: JSON.stringify(body) });
}
export function putEmployerJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, { method: "PUT", body: JSON.stringify(body) });
}
export function deleteEmployerJson<T>(path: string): Promise<T> {
  return requestJson<T>(path, { method: "DELETE" });
}
