"use client";

// アプリ全体の認証状態を保持する Context。
// - 起動時に localStorage の refresh token があれば /me でセッションを復元する
//   （access token はメモリのみなのでリロード直後は無い → client.ts が 401 を受けて自動 refresh する）
// - login / signup / logout を提供する
// - サーバー状態（メッセージ等）の取得は TanStack Query 側で行い、ここでは認証だけを扱う

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import { fetchMe, login as loginApi, signup as signupApi } from "@/lib/api/auth";
import { setUnauthorizedHandler } from "@/lib/api/client";
import { clearTokens, getRefreshToken } from "@/lib/auth/tokenStore";
import type { LoginRequest, MeResponse, SignupRequest } from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: MeResponse | null;
  login: (req: LoginRequest) => Promise<void>;
  signup: (req: SignupRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<MeResponse | null>(null);

  const setUnauthenticated = useCallback(() => {
    clearTokens();
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  // refresh も失敗した場合（セッション切れ）に client.ts から呼ばれる。
  useEffect(() => {
    setUnauthorizedHandler(setUnauthenticated);
    return () => setUnauthorizedHandler(null);
  }, [setUnauthenticated]);

  // 起動時のセッション復元。
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!getRefreshToken()) {
        if (!cancelled) setStatus("unauthenticated");
        return;
      }
      try {
        const me = await fetchMe();
        if (!cancelled) {
          setUser(me);
          setStatus("authenticated");
        }
      } catch {
        if (!cancelled) setUnauthenticated();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setUnauthenticated]);

  const login = useCallback(async (req: LoginRequest) => {
    await loginApi(req);
    const me = await fetchMe();
    setUser(me);
    setStatus("authenticated");
  }, []);

  const signup = useCallback(async (req: SignupRequest) => {
    await signupApi(req);
    const me = await fetchMe();
    setUser(me);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(() => {
    // バックエンドに失効 API が無いため、クライアント側のトークン破棄でログアウトする。
    setUnauthenticated();
  }, [setUnauthenticated]);

  return (
    <AuthContext.Provider value={{ status, user, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth は AuthProvider の内側で使う必要があります");
  }
  return ctx;
}
