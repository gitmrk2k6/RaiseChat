"use client";

// 入力中（typing）の受信フック。指定 destination を購読し「いま誰が入力中か」を返す。
//  - 受信のたびに該当ユーザーの表示を decayMs 後に消すタイマーを貼り直す（最後の受信から decayMs で消える）
//  - 自分（meId）は無視する
//  - backend は状態を持たず素通しするだけなので、消去はこのフロント側タイマーが真実源
//  - 確立方針どおり確定は WS 受信に一本化（楽観更新なし）

import { useCallback, useEffect, useRef, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { parseTypingEvent } from "./messages";
import { useStompSubscription } from "./useStompSubscription";

export function useTypingIndicator(
  destination: string | null,
  meId: string | null,
  decayMs = 4000,
): { userId: string; displayName: string }[] {
  const [typers, setTypers] = useState<Record<string, string>>({});
  const timers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  const handle = useCallback(
    (frame: IMessage) => {
      const event = parseTypingEvent(frame.body);
      if (!event) return;
      const id = String(event.userId);
      if (id === meId) return; // 自分は表示しない

      setTypers((prev) => ({ ...prev, [id]: event.displayName }));

      // 既存タイマーを貼り直し、最後の受信から decayMs で消す。
      clearTimeout(timers.current[id]);
      timers.current[id] = setTimeout(() => {
        delete timers.current[id];
        setTypers((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
      }, decayMs);
    },
    [meId, decayMs],
  );

  useStompSubscription(destination, handle);

  // destination 変更・アンマウント時に走っているタイマーを掃除する。
  useEffect(() => {
    const current = timers.current;
    return () => {
      Object.values(current).forEach(clearTimeout);
    };
  }, [destination]);

  return Object.entries(typers).map(([userId, displayName]) => ({ userId, displayName }));
}
