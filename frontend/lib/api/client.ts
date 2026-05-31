// REST API クライアントの薄いラッパ。
// 役割:
//  - ベース URL の解決（NEXT_PUBLIC_API_BASE 未設定なら相対 URL = next.config の rewrites プロキシ経由）
//  - Authorization: Bearer ヘッダの自動付与
//  - 401 を受けたら refresh token で 1 回だけ自動更新 → 元のリクエストを再試行
//  - エラーは ProblemDetail を解釈して ApiError として throw

import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from "@/lib/auth/tokenStore";
import { ApiError, type ProblemDetail } from "./problem";
import type { TokenResponse } from "./types";

// 空文字なら相対パス（同一オリジン → rewrites プロキシ）。本番で別ドメインなら絶対 URL を設定。
const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "";

// refresh も失敗した（＝セッション切れ）ときに呼ばれるハンドラ。AuthContext が登録する。
let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null): void {
  unauthorizedHandler = fn;
}

// 複数リクエストが同時に 401 を受けても refresh は 1 回だけにする（single-flight）。
let refreshPromise: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      clearTokens();
      return null;
    }
    const data = (await res.json()) as TokenResponse;
    setTokens(data);
    return data.accessToken;
  } catch {
    clearTokens();
    return null;
  }
}

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  /** JSON 化して送るリクエストボディ。FormData の場合はそのまま送る。 */
  body?: unknown;
  /** Authorization ヘッダを付けるか。既定 true。login/signup/refresh のみ false。 */
  auth?: boolean;
  /** 内部用: 401 後の再試行フラグ（無限ループ防止）。 */
  _retry?: boolean;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = true, _retry = false, headers, ...rest } = options;

  const h = new Headers(headers);
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isFormData) {
    h.set("Content-Type", "application/json");
  }
  if (auth) {
    const token = getAccessToken();
    if (token) h.set("Authorization", `Bearer ${token}`);
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: h,
    body: body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
  });

  // 401 → アクセストークン切れ。refresh して 1 回だけ再試行。
  if (res.status === 401 && auth && !_retry) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      return apiRequest<T>(path, { ...options, _retry: true });
    }
    // refresh も失敗 → セッション切れとして扱う。
    clearTokens();
    unauthorizedHandler?.();
    throw await toApiError(res);
  }

  if (!res.ok) {
    throw await toApiError(res);
  }

  // 204 No Content / 空ボディに対応。
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function toApiError(res: Response): Promise<ApiError> {
  let problem: ProblemDetail | null = null;
  try {
    problem = (await res.json()) as ProblemDetail;
  } catch {
    // ボディが空、または JSON でない場合はそのまま。
  }
  const message = problem?.detail || problem?.title || res.statusText || `HTTP ${res.status}`;
  return new ApiError(res.status, problem, message);
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: "GET" }),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: "POST", body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: "PUT", body }),
  patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: "PATCH", body }),
  delete: <T>(path: string, opts?: RequestOptions) =>
    apiRequest<T>(path, { ...opts, method: "DELETE" }),
};
