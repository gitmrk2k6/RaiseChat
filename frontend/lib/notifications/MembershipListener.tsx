"use client";

// F-16 メンバーシップ剥奪のリアルタイム反映。
//  - /user/queue/notifications の WORKSPACE_REMOVED / CHANNEL_REMOVED を購読する
//  - 受信したら react-query を無効化（rail / サイドバーから即座に消える）
//  - 今まさに開いている WS / チャンネルから外れた場合は安全な場所へリダイレクトする
//  - 何が起きたかを画面上部の一時バナーで知らせる
// 確立方針どおり確定は WS 受信に一本化（楽観更新なし）。本人宛キューに本人分のみ届くため自分判定は不要。
// StompProvider・QueryClientProvider の内側、NotificationProvider と並べて置くこと。

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { IMessage } from "@stomp/stompjs";
import { useAuth } from "@/lib/auth/AuthContext";
import { useStompSubscription } from "@/lib/ws/useStompSubscription";
import { queryKeys } from "@/lib/api/queryKeys";
import type { NotificationEventDto } from "@/lib/api/types";

const BANNER_MS = 6000;

export function MembershipListener() {
  const { status } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();

  // リダイレクト判定で最新の pathname を参照したいが、handler の再生成で再購読させたくないため ref に逃がす。
  const pathnameRef = useRef(pathname);
  pathnameRef.current = pathname;

  const [banner, setBanner] = useState<string | null>(null);
  const bannerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showBanner = useCallback((message: string) => {
    setBanner(message);
    if (bannerTimer.current) clearTimeout(bannerTimer.current);
    bannerTimer.current = setTimeout(() => setBanner(null), BANNER_MS);
  }, []);

  useEffect(() => () => {
    if (bannerTimer.current) clearTimeout(bannerTimer.current);
  }, []);

  const handleEvent = useCallback(
    (frame: IMessage) => {
      let event: NotificationEventDto;
      try {
        event = JSON.parse(frame.body) as NotificationEventDto;
      } catch {
        return;
      }

      const here = pathnameRef.current ?? "";

      // リダイレクトを伴う場合、router.push と同時に invalidate すると進行中の再取得が
      // 遷移で打ち切られ、サイドバー/レールが古いまま残る。退避してから次フレームで無効化し、
      // 遷移後の安定したツリーで確実に再取得させる（リダイレクトしない場合は即時で良い）。
      const invalidateAfter = (redirected: boolean, run: () => void) => {
        if (redirected) {
          requestAnimationFrame(run);
        } else {
          run();
        }
      };

      if (event.type === "WORKSPACE_REMOVED") {
        const wsId = String(event.scopeId);
        // 当該 WS を開いていたら一覧へ退避する。
        const viewing = here.startsWith(`/workspaces/${wsId}`);
        if (viewing) {
          router.push("/workspaces");
        }
        // "workspaces" ルート配下（一覧 / 詳細 / channels / dmRooms / search）をまとめて無効化。
        invalidateAfter(viewing, () =>
          queryClient.invalidateQueries({ queryKey: ["workspaces"] }),
        );
        showBanner("このワークスペースから外れました");
        return;
      }

      if (event.type === "CHANNEL_REMOVED") {
        const channelId = String(event.scopeId);
        // 当該チャンネルを開いていたら同じ WS のホームへ退避する。
        const match = here.match(/^\/workspaces\/([^/]+)\/channels\/([^/]+)/);
        const viewing = !!match && match[2] === channelId;
        if (viewing && match) {
          router.push(`/workspaces/${match[1]}`);
        }
        invalidateAfter(viewing, () => {
          queryClient.invalidateQueries({ queryKey: ["workspaces"] });
          queryClient.invalidateQueries({ queryKey: queryKeys.channel(channelId) });
        });
        showBanner("チャンネルが削除されました");
        return;
      }
      // 未読系（UNREAD_*）は NotificationContext が処理するため無視。
    },
    [queryClient, router, showBanner],
  );

  useStompSubscription(
    status === "authenticated" ? "/user/queue/notifications" : null,
    handleEvent,
  );

  if (!banner) return null;

  return (
    <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50">
      <div className="rounded-md bg-gray-900 text-white text-sm px-4 py-2 shadow-lg">
        {banner}
      </div>
    </div>
  );
}
