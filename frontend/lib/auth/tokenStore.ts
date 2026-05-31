// JWT の保管。
// - access token: メモリ（モジュール変数）のみ。リロードで消えるが、起動時に refresh で復元する。
//   localStorage に置かないことで XSS 時の持ち出しリスクを下げる。
// - refresh token: localStorage。リロードをまたいでセッションを復元するために永続化が必要。
//   バックエンドは refresh のたびにトークンを回転させるため、新しい値で上書きする。

import type { TokenResponse } from "@/lib/api/types";

const REFRESH_TOKEN_KEY = "raisechat.refreshToken";

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string | null): void {
  if (typeof window === "undefined") return;
  if (token) {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
}

/** login / signup / refresh のレスポンスをまとめて保存する。 */
export function setTokens(tokens: TokenResponse): void {
  setAccessToken(tokens.accessToken);
  setRefreshToken(tokens.refreshToken);
}

/** ログアウトや認証失敗時にすべて破棄する。 */
export function clearTokens(): void {
  setAccessToken(null);
  setRefreshToken(null);
}
