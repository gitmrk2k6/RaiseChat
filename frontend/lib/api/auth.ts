// 認証関連の API 呼び出し。
import { api } from "./client";
import { setTokens } from "@/lib/auth/tokenStore";
import type { LoginRequest, MeResponse, SignupRequest, TokenResponse } from "./types";

export async function login(req: LoginRequest): Promise<TokenResponse> {
  const res = await api.post<TokenResponse>("/api/auth/login", req, { auth: false });
  setTokens(res);
  return res;
}

export async function signup(req: SignupRequest): Promise<TokenResponse> {
  const res = await api.post<TokenResponse>("/api/auth/signup", req, { auth: false });
  setTokens(res);
  return res;
}

export async function fetchMe(): Promise<MeResponse> {
  return api.get<MeResponse>("/api/auth/me");
}
