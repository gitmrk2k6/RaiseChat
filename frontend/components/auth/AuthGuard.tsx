"use client";

// 認証が必要な画面を保護するガード。
// - 復元中（loading）はローディング表示
// - 未認証なら /login へリダイレクト
// - 認証済みのときだけ children を表示する

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthContext";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    }
  }, [status, router]);

  if (status !== "authenticated") {
    return (
      <div className="h-screen flex items-center justify-center text-sm text-gray-500">
        読み込み中…
      </div>
    );
  }

  return <>{children}</>;
}
