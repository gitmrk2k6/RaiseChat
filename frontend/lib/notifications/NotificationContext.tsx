"use client";

// F-14 未読/メンション数の単一の真実源（フロント側）。
//  - 認証済みになったら GET /api/notifications/unread で seed
//  - /user/queue/notifications を購読し、各スコープを「絶対値で上書き」（CLEARED は 0）
//  - useUnread() で scope 単位の未読/メンションを引ける
// 確立方針どおり楽観更新はしない。既読化は POST を投げるだけで、0 化はサーバ発 WS に委ねる。
// StompProvider の内側に置くこと（WS 接続状態に追従して購読するため）。

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { useAuth } from "@/lib/auth/AuthContext";
import { getUnread } from "@/lib/api/notifications";
import { useStompSubscription } from "@/lib/ws/useStompSubscription";
import type { NotificationEventDto } from "@/lib/api/types";

/** 1 スコープ分の未読数。mention は unread の内数。 */
export interface UnreadCount {
  unread: number;
  mention: number;
}

interface NotificationContextValue {
  getChannelUnread: (channelId: string) => UnreadCount;
  getDmUnread: (dmRoomId: string) => UnreadCount;
}

const ZERO: UnreadCount = { unread: 0, mention: 0 };

const NotificationContext = createContext<NotificationContextValue>({
  getChannelUnread: () => ZERO,
  getDmUnread: () => ZERO,
});

// scope の合成キー。backend の UnreadItem.scopeType（"channel"|"dm"）と揃える。
const channelKey = (id: string | number) => `channel:${id}`;
const dmKey = (id: string | number) => `dm:${id}`;

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const [counts, setCounts] = useState<Record<string, UnreadCount>>({});

  // 認証が切れたらクリア。認証済みになったら未読一覧で seed する。
  useEffect(() => {
    if (status !== "authenticated") {
      setCounts({});
      return;
    }
    let cancelled = false;
    getUnread()
      .then((res) => {
        if (cancelled) return;
        const seeded: Record<string, UnreadCount> = {};
        for (const item of res.items) {
          const key = item.scopeType === "dm" ? dmKey(item.scopeId) : channelKey(item.scopeId);
          seeded[key] = { unread: item.unreadCount, mention: item.mentionCount };
        }
        // seed と購読の間に届いたイベントの方が新しいので、既存（イベント由来）を優先して残す。
        setCounts((prev) => ({ ...seeded, ...prev }));
      })
      .catch(() => {
        // 失敗してもバッジ無しで継続（致命ではない）。
      });
    return () => {
      cancelled = true;
    };
  }, [status]);

  // /user/queue/notifications を購読。各イベントで該当スコープを絶対値で上書きする。
  const handleEvent = useCallback((frame: IMessage) => {
    let event: NotificationEventDto;
    try {
      event = JSON.parse(frame.body) as NotificationEventDto;
    } catch {
      return;
    }
    // 同じキューに membership 剥奪イベント（WORKSPACE_REMOVED / CHANNEL_REMOVED）も流れてくる。
    // ここは未読の真実源なので未読系だけを処理し、剥奪系は MembershipListener に委ねる。
    if (event.type !== "UNREAD_UPDATED" && event.type !== "UNREAD_CLEARED") {
      return;
    }
    const key = event.scopeType === "dm" ? dmKey(event.scopeId) : channelKey(event.scopeId);
    setCounts((prev) => ({
      ...prev,
      [key]: { unread: event.unreadCount, mention: event.mentionCount },
    }));
  }, []);

  useStompSubscription(
    status === "authenticated" ? "/user/queue/notifications" : null,
    handleEvent,
  );

  const getChannelUnread = useCallback(
    (id: string) => counts[channelKey(id)] ?? ZERO,
    [counts],
  );
  const getDmUnread = useCallback(
    (id: string) => counts[dmKey(id)] ?? ZERO,
    [counts],
  );

  return (
    <NotificationContext.Provider value={{ getChannelUnread, getDmUnread }}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useUnread(): NotificationContextValue {
  return useContext(NotificationContext);
}
