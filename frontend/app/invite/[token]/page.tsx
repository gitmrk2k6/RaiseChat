"use client";

// 招待受諾ページ（F-15）。招待リンク（INVITE_BASE_URL/{token}）の着地先。
// - 未ログイン: /login?next=/invite/{token} へ送り、ログイン/サインアップ後にここへ戻す
// - ログイン済み: POST /api/invites/{token}/accept で参加 → 当該ワークスペースへ遷移
// - 404（不正/削除済み）・410（期限切れ/無効化/上限超過）はエラー表示
//
// このページは (auth) でも workspaces でもないルートグループ外のため、レイアウトは自前で持つ。

import { useEffect, useRef } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthContext";
import { acceptInvite } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { ApiError } from "@/lib/api/problem";

export default function InviteAcceptPage() {
  const { token } = useParams<{ token: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { status } = useAuth();
  // 認証完了後に accept を 1 回だけ実行するためのガード（再レンダー / Strict Mode の二重実行防止）。
  const triggered = useRef(false);

  const mutation = useMutation({
    mutationFn: () => acceptInvite(token),
    onSuccess: async (ws) => {
      // 参加した WS をサイドバー/一覧へ反映してから遷移する。
      await queryClient.invalidateQueries({ queryKey: queryKeys.workspaces });
      router.replace(`/workspaces/${ws.id}`);
    },
  });

  // 認証状態が決まったら分岐する。
  useEffect(() => {
    if (status === "unauthenticated") {
      const next = encodeURIComponent(`/invite/${token}`);
      router.replace(`/login?next=${next}`);
      return;
    }
    if (status === "authenticated" && !triggered.current) {
      triggered.current = true;
      mutation.mutate();
    }
  }, [status, token, router, mutation]);

  const error = mutation.error;
  const status404 = error instanceof ApiError && error.status === 404;
  const status410 = error instanceof ApiError && error.status === 410;

  return (
    <div className="min-h-screen bg-gradient-to-b from-purple-50 to-white flex flex-col">
      <header className="px-8 py-5">
        <div className="text-2xl font-bold text-slack-aubergine">RaiseChat</div>
      </header>
      <main className="flex-1 flex items-center justify-center px-4">
        <div className="w-full max-w-md bg-white rounded-lg shadow-md p-8 text-center">
          {error ? (
            <>
              <h1 className="text-xl font-bold text-slack-aubergine mb-3">
                招待を受諾できませんでした
              </h1>
              <p className="text-sm text-gray-600 mb-6">
                {status404
                  ? "この招待リンクは無効です。URL が正しいか、発行者にご確認ください。"
                  : status410
                    ? "この招待リンクは有効期限切れ、または無効化されています。発行者に新しいリンクを依頼してください。"
                    : error instanceof ApiError
                      ? error.message
                      : "時間をおいて再度お試しください。"}
              </p>
              <Link
                href="/workspaces"
                className="inline-block bg-slack-aubergine text-white font-bold px-5 py-2.5 rounded hover:bg-slack-aubergineHover transition"
              >
                ワークスペース一覧へ
              </Link>
            </>
          ) : (
            <>
              <h1 className="text-xl font-bold text-slack-aubergine mb-3">
                ワークスペースに参加しています…
              </h1>
              <p className="text-sm text-gray-600">少々お待ちください。</p>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
