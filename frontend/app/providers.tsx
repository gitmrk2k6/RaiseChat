"use client";

// クライアント側の横断 Provider をまとめる。
// - TanStack Query: サーバー状態（メッセージ・チャンネル等）のキャッシュと再取得を担う
// - AuthProvider: 認証状態

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/lib/auth/AuthContext";

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
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}
