// オンライン状態（presence）の API 呼び出し。
// number↔string 変換をここに封じ込め、上位（PresenceContext）は string id の集合だけ扱う。

import { api } from "./client";
import type { PresenceResponseDto } from "./types";

/** GET /api/presence 現在オンラインなユーザーの id 一覧（string 化して返す）。 */
export async function getOnlineUserIds(): Promise<string[]> {
  const res = await api.get<PresenceResponseDto>("/api/presence");
  return res.userIds.map(String);
}
