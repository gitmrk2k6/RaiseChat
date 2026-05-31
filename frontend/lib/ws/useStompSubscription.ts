"use client";

// 指定 destination を購読する汎用フック。
//  - connected が true の間だけ購読し、(再)接続時に自動で貼り直す
//  - アンマウント / destination 変更 / 切断時に unsubscribe する
//  - handler は ref 経由で最新を参照するため、handler の再生成で再購読は起きない

import { useEffect, useRef } from "react";
import type { IMessage } from "@stomp/stompjs";
import { getStompClient } from "./client";
import { useStomp } from "./StompProvider";

export function useStompSubscription(
  destination: string | null,
  handler: (message: IMessage) => void,
): void {
  const { connected } = useStomp();
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    if (!connected || !destination) return;
    const sub = getStompClient().subscribe(destination, (msg) =>
      handlerRef.current(msg),
    );
    return () => sub.unsubscribe();
  }, [connected, destination]);
}
