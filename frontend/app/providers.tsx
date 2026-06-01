"use client";

// クライアント側の横断 Provider をまとめる。
// - TanStack Query: サーバー状態（メッセージ・チャンネル等）のキャッシュと再取得を担う
// - AuthProvider: 認証状態
// - StompProvider: 認証済みのとき WebSocket(STOMP) に接続。AuthProvider の内側に置く
// - NotificationProvider: F-14 未読/メンション数の真実源。WS 購読のため StompProvider の内側に置く

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/lib/auth/AuthContext";
import { StompProvider } from "@/lib/ws/StompProvider";
import { NotificationProvider } from "@/lib/notifications/NotificationContext";

export function Providers({ children }: { children: React.ReactNode }) {
  // QueryClient はマウントごとに作り直さないよう useState の初期化関数で 1 度だけ生成する。
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <StompProvider>
          <NotificationProvider>{children}</NotificationProvider>
        </StompProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
