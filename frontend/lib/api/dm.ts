// DM ルーム関連の API 呼び出し（F-06）。
// DTO（id は数値 BIGINT、members は User サブセット）をフロントの DmRoom へ変換する。
// API が返さない表示専用フィールドはプレースホルダ補完する（統合2 と同方針）:
//  - lastMessagePreview → undefined（一覧 API に含まれない）
//  - unreadCount        → 0        （型を満たすための既定値。実値は描画時に
//    NotificationProvider(useUnread) が scope 単位で上書きする。F-14 接続済み）
//  - members[].avatarColor → member.id から決定論的に導出（messages の avatarColorFor を共用）

import { api } from "./client";
import type { DmRoomDto, Page } from "./types";
import { avatarColorFor } from "./messages";
import type { DmRoom } from "@/types";

/** DmRoomDto → フロントの DmRoom。id を string 化し、表示用 members とプレースホルダを補完。 */
export function toDmRoom(dto: DmRoomDto): DmRoom {
  return {
    id: String(dto.id),
    workspaceId: String(dto.workspaceId),
    memberIds: dto.members.map((m) => String(m.id)),
    members: dto.members.map((m) => ({
      id: String(m.id),
      displayName: m.displayName,
      avatarColor: avatarColorFor(String(m.id)),
    })),
    lastMessagePreview: undefined,
    unreadCount: 0,
  };
}

/** GET /api/workspaces/{wsId}/dm/rooms 自分が参加している DM ルーム一覧。 */
export async function listDmRooms(workspaceId: string): Promise<DmRoom[]> {
  const page = await api.get<Page<DmRoomDto>>(
    `/api/workspaces/${Number(workspaceId)}/dm/rooms`,
  );
  return page.items.map(toDmRoom);
}

/**
 * POST /api/workspaces/{wsId}/dm/rooms 相手との DM ルームを作成または取得する。
 * 既存ルームがあれば 200、新規なら 201 だがレスポンス形は同じ（冪等）。
 */
export async function createDmRoom(
  workspaceId: string,
  partnerUserId: string,
): Promise<DmRoom> {
  const dto = await api.post<DmRoomDto>(
    `/api/workspaces/${Number(workspaceId)}/dm/rooms`,
    { partnerUserId: Number(partnerUserId) },
  );
  return toDmRoom(dto);
}
