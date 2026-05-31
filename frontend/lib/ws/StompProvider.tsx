"use client";

// STOMP 接続のライフサイクルを認証状態に連動させる Provider。
//  - useAuth().status が "authenticated" になった時点で activate
//    （この時点で必ず一度 API 成功＝access token がメモリに載っているので CONNECT 認証が通る）
//  - それ以外（unauthenticated / loading）では deactivate
//  - 接続/切断を connected フラグとして配り、購読フックが (再)接続時に貼り直せるようにする

import {
  createContext,
  useContext,
  useEffect,
  useState,
} from "react";
import { useAuth } from "@/lib/auth/AuthContext";
import { activateStomp, deactivateStomp, getStompClient } from "./client";

interface StompContextValue {
  /** STOMP CONNECTED 済みか。購読フックはこれが true の間だけ購読する。 */
  connected: boolean;
}

const StompContext = createContext<StompContextValue>({ connected: false });

export function StompProvider({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (status !== "authenticated") {
      // 未認証・復元中は接続しない。既に接続済みなら閉じる。
      deactivateStomp();
      setConnected(false);
      return;
    }

    const client = getStompClient();
    client.onConnect = () => setConnected(true);
    client.onWebSocketClose = () => setConnected(false);
    client.onStompError = () => setConnected(false);
    activateStomp();

    return () => {
      // ハンドラを外す（次の activate でも beforeConnect が最新トークンを載せる）。
      client.onConnect = () => {};
      client.onWebSocketClose = () => {};
      client.onStompError = () => {};
    };
  }, [status]);

  return (
    <StompContext.Provider value={{ connected }}>
      {children}
    </StompContext.Provider>
  );
}

export function useStomp(): StompContextValue {
  return useContext(StompContext);
}
