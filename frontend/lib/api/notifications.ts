// 未読/メンション数の API 呼び出し（F-14）。
// 未読一覧の取得と既読化を扱う。scope キー（"channel:ID" / "dm:ID"）への正規化は
// 上位の NotificationProvider 側で行い、ここは number↔string 変換のみ封じ込める。

import { api } from "./client";
import type { UnreadResponseDto } from "./types";

/** GET /api/notifications/unread 未読のあるスコープ一覧（未読 0 のスコープは含まれない）。 */
export async function getUnread(): Promise<UnreadResponseDto> {
  return api.get<UnreadResponseDto>("/api/notifications/unread");
}

/** POST /api/channels/{id}/read チャンネルを最新まで既読化する（body 無し＝最新）。 */
export async function markChannelRead(channelId: string): Promise<void> {
  await api.post<void>(`/api/channels/${Number(channelId)}/read`);
}

/** POST /api/dm/rooms/{id}/read DM ルームを最新まで既読化する（body 無し＝最新）。 */
export async function markDmRead(dmRoomId: string): Promise<void> {
  await api.post<void>(`/api/dm/rooms/${Number(dmRoomId)}/read`);
}
