"use client";

// 入力中（typing）の送信を throttle するフック。
//  - 打鍵のたびに呼んでも、最後の送信から intervalMs 未満なら送らない（高頻度送信を抑える）
//  - 確立方針どおり送信は WS。状態は持たず、消えるのは受信側タイマーに委ねる
//  - send は messages.ts の publishChannelTyping / publishDmTyping を想定（ref で最新を参照）

import { useCallback, useRef } from "react";

export function useTypingNotifier(
  send: () => void,
  intervalMs = 2500,
): () => void {
  const sendRef = useRef(send);
  sendRef.current = send;
  const lastSentRef = useRef(0);

  return useCallback(() => {
    const now = Date.now();
    if (now - lastSentRef.current < intervalMs) return;
    lastSentRef.current = now;
    sendRef.current();
  }, [intervalMs]);
}
