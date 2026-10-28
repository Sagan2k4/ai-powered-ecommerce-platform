const TOKEN_KEY = "aishop.token";
export const UNAUTHORIZED_EVENT = "aishop:unauthorized";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage unavailable (private mode); the session just won't persist.
  }
}

type Options = { method?: string; body?: unknown; signal?: AbortSignal };

async function request(path: string, { method = "GET", body, signal }: Options = {}): Promise<Response> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });

  if (!res.ok) {
    if (res.status === 401 && token) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    let message = `Request failed (${res.status})`;
    try {
      const problem = (await res.json()) as { detail?: string; title?: string };
      message = problem.detail || problem.title || message;
    } catch {
      // Non-JSON error body.
    }
    throw new ApiError(res.status, message);
  }
  return res;
}

/** Calls the Spring Boot API through the same-origin `/api` proxy, attaching the JWT if signed in. */
export async function api<T>(path: string, options?: Options): Promise<T> {
  const res = await request(path, options);
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

export async function downloadFile(path: string, fallbackName: string) {
  const res = await request(path);
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const filename = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : "Something went wrong";
}
