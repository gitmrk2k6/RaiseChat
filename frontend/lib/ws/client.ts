// STOMP over SockJS クライアントのシングルトン。
// 役割:
//  - /ws への SockJS 接続（バックエンドが .withSockJS() のため raw WebSocket ではなく SockJS を使う）
//  - 接続/再接続のたびに beforeConnect で最新の access token を Authorization ヘッダへ載せ直す
//    （access token はメモリ保持で refresh のたびに回転しうるため、CONNECT 直前に取り直すのが安全）
//  - reconnectDelay による自動再接続
//  - activate / deactivate は認証状態に連動して StompProvider が呼ぶ

import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getAccessToken } from "@/lib/auth/tokenStore";

// 未設定ならローカル開発の既定値。REST の API_BASE と違い、SockJS は絶対 URL を要求する。
const WS_BASE = process.env.NEXT_PUBLIC_WS_BASE ?? "http://localhost:8080";

let client: Client | null = null;

/** シングルトンの STOMP Client を返す（無ければ生成）。activate はまだ呼ばない。 */
export function getStompClient(): Client {
  if (client) return client;

  client = new Client({
    // brokerURL は使わず webSocketFactory で SockJS を返す。呼ばれるたびに新しい socket を生成する。
    webSocketFactory: () => new SockJS(`${WS_BASE}/ws`),
    // 再接続を有効化（ミリ秒）。0 にすると再接続しない。
    reconnectDelay: 5000,
    // 接続/再接続の直前に毎回呼ばれる。最新トークンを取り直してヘッダへ載せる。
    beforeConnect: (c) => {
      const token = getAccessToken();
      c.configure({
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      });
    },
  });

  return client;
}

/** 接続を開始する（多重 activate は stompjs 側で無害）。 */
export function activateStomp(): void {
  getStompClient().activate();
}

/** 接続を破棄する（ログアウト時など）。 */
export function deactivateStomp(): void {
  client?.deactivate();
}
