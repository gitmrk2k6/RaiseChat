// WebSocket メッセージ送受信のヘルパとイベント型。
// バックエンドの実ワイヤ形式に合わせる（docs/realtime-design.md とは一部差異あり）:
//  - エンベロープは { type, payload }（serverTime 無し）
//  - type は enum 名の大文字スネーク（例 "MESSAGE_CREATED"）
//  - MESSAGE_CREATED の payload は REST 履歴の MessageDto と同形 → toMessage() を再利用できる
//  - 送信ボディは { body, parentMessageId }（clientMessageId 無し）

import { getStompClient } from "./client";
import { toMessage } from "@/lib/api/messages";
import type { MessageDto } from "@/lib/api/types";
import type { Message } from "@/types";

/** バックエンド WsEvent.EventType（enum 名）。今単位では MESSAGE_CREATED のみ扱う。 */
export type WsEventType =
  | "MESSAGE_CREATED"
  | "MESSAGE_EDITED"
  | "MESSAGE_DELETED"
  | "REACTION_ADDED"
  | "REACTION_REMOVED";

/** /topic/** で配信される共通エンベロープ。 */
export interface WsEvent<P = unknown> {
  type: WsEventType;
  payload: P;
}

/** STOMP MESSAGE フレームの body(JSON 文字列) を WsEvent にパースする。失敗時は null。 */
export function parseWsEvent(body: string): WsEvent | null {
  try {
    return JSON.parse(body) as WsEvent;
  } catch {
    return null;
  }
}

/** MESSAGE_CREATED イベントなら payload を Message へ変換して返す。それ以外は null。 */
export function messageCreatedToMessage(event: WsEvent): Message | null {
  if (event.type !== "MESSAGE_CREATED") return null;
  return toMessage(event.payload as MessageDto);
}

/** チャンネルへ新規メッセージを送信する（/app/channels/{id}/messages）。 */
export function publishChannelMessage(
  channelId: string,
  body: string,
  parentMessageId?: string | null,
): void {
  getStompClient().publish({
    destination: `/app/channels/${Number(channelId)}/messages`,
    body: JSON.stringify({
      body,
      parentMessageId: parentMessageId != null ? Number(parentMessageId) : null,
    }),
  });
}

/** DM ルームへ新規メッセージを送信する（/app/dm/{roomId}/messages）。 */
export function publishDmMessage(
  roomId: string,
  body: string,
  parentMessageId?: string | null,
): void {
  getStompClient().publish({
    destination: `/app/dm/${Number(roomId)}/messages`,
    body: JSON.stringify({
      body,
      parentMessageId: parentMessageId != null ? Number(parentMessageId) : null,
    }),
  });
}
