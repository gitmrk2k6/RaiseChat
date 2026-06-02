"use client";

// オンライン状態（presence）の単一の真実源（フロント側）。Slack 流にユーザー単位グローバル。
//  - 認証済みになったら GET /api/presence で「現在オンラインな id 集合」を seed
//  - /topic/presence を購読し、PresenceEvent ごとに集合へ add（online）/ delete（offline）
//  - usePresence(userId) で個別ユーザーのオンライン判定を引く
// 確立方針どおり確定は WS 受信に一本化（楽観更新なし）。
// StompProvider の内側に置くこと（WS 接続状態に追従して購読・再 seed するため）。

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { useAuth } from "@/lib/auth/AuthContext";
import { getOnlineUserIds } from "@/lib/api/presence";
import { useStomp } from "@/lib/ws/StompProvider";
import { useStompSubscription } from "@/lib/ws/useStompSubscription";
import type { PresenceEventDto } from "@/lib/api/types";

interface PresenceContextValue {
  /** 指定ユーザー（string id）がオンラインか。 */
  isOnline: (userId: string | null | undefined) => boolean;
}

const PresenceContext = createContext<PresenceContextValue>({
  isOnline: () => false,
});

export function PresenceProvider({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const { connected } = useStomp();
  const [online, setOnline] = useState<ReadonlySet<string>>(new Set());

  // 認証が切れたらクリア。接続確立のたびに seed し直す（再接続で取りこぼした変化を埋める）。
  useEffect(() => {
    if (status !== "authenticated" || !connected) {
      setOnline(new Set());
      return;
    }
    let cancelled = false;
    getOnlineUserIds()
      .then((ids) => {
        if (cancelled) return;
        // seed と購読の間に届いたイベントの方が新しいので、既存（イベント由来）に seed を併合する。
        setOnline((prev) => {
          const next = new Set(prev);
          ids.forEach((id) => next.add(id));
          return next;
        });
      })
      .catch(() => {
        // 失敗してもドット無しで継続（致命ではない）。
      });
    return () => {
      cancelled = true;
    };
  }, [status, connected]);

  // /topic/presence を購読。online=true で集合へ追加、false で除去。
  const handleEvent = useCallback((frame: IMessage) => {
    let event: PresenceEventDto;
    try {
      event = JSON.parse(frame.body) as PresenceEventDto;
    } catch {
      return;
    }
    if (typeof event.userId !== "number" || typeof event.online !== "boolean") return;
    const id = String(event.userId);
    setOnline((prev) => {
      const has = prev.has(id);
      if (event.online === has) return prev; // 変化なしなら再レンダリングを避ける
      const next = new Set(prev);
      if (event.online) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);

  useStompSubscription(
    status === "authenticated" ? "/topic/presence" : null,
    handleEvent,
  );

  const isOnline = useCallback(
    (userId: string | null | undefined) => (userId != null && online.has(userId)),
    [online],
  );

  return (
    <PresenceContext.Provider value={{ isOnline }}>
      {children}
    </PresenceContext.Provider>
  );
}

export function usePresence(): PresenceContextValue {
  return useContext(PresenceContext);
}
