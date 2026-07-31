const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "");

/**
 * 현재는 API 미연동 상태에서도 화면을 확인할 수 있도록 fallback을 받는다.
 * 실제 Spring API가 준비되면 VITE_API_BASE_URL만 설정해 fixture를 제거한다.
 */
export async function getJson<T>(path: string, fallback: T): Promise<T> {
  if (!apiBaseUrl) {
    return fallback;
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    throw new Error(`GET ${path} failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export async function postJson<T>(path: string, body: unknown, fallback: T): Promise<T> {
  if (!apiBaseUrl) {
    return fallback;
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`POST ${path} failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}
