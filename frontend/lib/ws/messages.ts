// WebSocket メッセージ送受信のヘルパとイベント型。
// バックエンドの実ワイヤ形式に合わせる（docs/realtime-design.md とは一部差異あり）:
//  - エンベロープは { type, payload }（serverTime 無し）
//  - type は enum 名の大文字スネーク（例 "MESSAGE_CREATED"）
//  - MESSAGE_CREATED の payload は REST 履歴の MessageDto と同形 → toMessage() を再利用できる
//  - 送信ボディは { body, parentMessageId }（clientMessageId 無し）

import { getStompClient } from "./client";
import { toMessage } from "@/lib/api/messages";
import type { MessageDto, ReactionDto } from "@/lib/api/types";
import type { Message, Reaction } from "@/types";

/** バックエンド WsEvent.EventType（enum 名）。 */
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

/**
 * MESSAGE_EDITED を messages へ適用する（body / editedAt / mentions / attachments を更新）。
 * payload には reactions / thread 系が含まれないため、それらは既存値を保持する（潰さない）。
 */
export function applyEdited(messages: Message[], event: WsEvent): Message[] {
  if (event.type !== "MESSAGE_EDITED") return messages;
  const dto = event.payload as MessageDto;
  const id = String(dto.id);
  return messages.map((m) => {
    if (m.id !== id) return m;
    const updated = toMessage(dto);
    return {
      ...updated,
      reactions: m.reactions,
      threadReplyCount: m.threadReplyCount,
      threadParticipantIds: m.threadParticipantIds,
    };
  });
}

/** MESSAGE_DELETED の対象メッセージ id（string）を返す。それ以外は null。 */
export function deletedMessageId(event: WsEvent): string | null {
  if (event.type !== "MESSAGE_DELETED") return null;
  return String((event.payload as MessageDto).id);
}

/**
 * REACTION_ADDED / REACTION_REMOVED を messages へ適用する。
 * payload は emoji 単位の集計（セット型）なので、userIds をそのまま上書きする
 * → 自分の操作の echo でも、他人の操作でも冪等に収束する。userIds が空なら該当 emoji を消す。
 */
export function applyReaction(messages: Message[], event: WsEvent): Message[] {
  if (event.type !== "REACTION_ADDED" && event.type !== "REACTION_REMOVED") {
    return messages;
  }
  const dto = event.payload as ReactionDto;
  const id = String(dto.messageId);
  const userIds = dto.userIds.map(String);
  return messages.map((m) => {
    if (m.id !== id) return m;
    let reactions: Reaction[];
    if (userIds.length === 0) {
      reactions = m.reactions.filter((r) => r.emoji !== dto.emoji);
    } else if (m.reactions.some((r) => r.emoji === dto.emoji)) {
      reactions = m.reactions.map((r) => (r.emoji === dto.emoji ? { ...r, userIds } : r));
    } else {
      reactions = [...m.reactions, { emoji: dto.emoji, userIds }];
    }
    return { ...m, reactions };
  });
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

/**
 * typing（入力中）イベント。短命・揮発なため永続化されず、受信側はタイマーで消す。
 * userId は数値 id（受信側で自分を除外するのに使う）、displayName は「○○ が入力中…」表示用。
 */
export interface TypingEvent {
  userId: number;
  displayName: string;
}

/** /topic 配下の typing フレーム（…/typing）をパースする。失敗時は null。 */
export function parseTypingEvent(body: string): TypingEvent | null {
  try {
    const e = JSON.parse(body) as TypingEvent;
    if (typeof e.userId !== "number" || typeof e.displayName !== "string") return null;
    return e;
  } catch {
    return null;
  }
}

/** チャンネルで入力中であることを送信する（/app/channels/{id}/typing、ボディ不要）。 */
export function publishChannelTyping(channelId: string): void {
  getStompClient().publish({
    destination: `/app/channels/${Number(channelId)}/typing`,
    body: "",
  });
}

/** DM ルームで入力中であることを送信する（/app/dm/{roomId}/typing、ボディ不要）。 */
export function publishDmTyping(roomId: string): void {
  getStompClient().publish({
    destination: `/app/dm/${Number(roomId)}/typing`,
    body: "",
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
