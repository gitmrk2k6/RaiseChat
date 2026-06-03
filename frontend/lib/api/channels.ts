// チャンネル関連の API 呼び出し。
// DTO（id は数値 BIGINT, type は大文字）をフロントの ViewModel（id は string, type は小文字）へ変換する。
// 一覧 API に含まれないフィールド（topic 以外のメタ）はプレースホルダで補完する:
//  - topic    ← API の description
//  - unreadCount / hasMention → 0 / false（型を満たすための既定値。実値は描画時に
//    NotificationProvider(useUnread) が scope 単位で上書きする。F-14 接続済み）
//  - memberIds → []（一覧 API に含まれない）

import { api } from "./client";
import type { ChannelDto, Page } from "./types";
import type { Channel } from "@/types";

/** ChannelDto → フロントの Channel。id を string 化し、type を小文字へ正規化する。 */
export function toChannel(dto: ChannelDto): Channel {
  return {
    id: String(dto.id),
    workspaceId: String(dto.workspaceId),
    name: dto.name,
    type: dto.type === "PRIVATE" ? "private" : "public",
    topic: dto.description ?? undefined,
    memberIds: [],
    unreadCount: 0,
    hasMention: false,
  };
}

/** GET /api/workspaces/{wsId}/channels ワークスペース内チャンネル一覧（自分が見える範囲）。 */
export async function listChannels(workspaceId: string): Promise<Channel[]> {
  const page = await api.get<Page<ChannelDto>>(
    `/api/workspaces/${Number(workspaceId)}/channels`,
  );
  return page.items.map(toChannel);
}

/** GET /api/channels/{id} チャンネル詳細。 */
export async function getChannel(id: string): Promise<Channel> {
  const dto = await api.get<ChannelDto>(`/api/channels/${Number(id)}`);
  return toChannel(dto);
}

/**
 * POST /api/workspaces/{wsId}/channels チャンネルを新規作成する。
 * name は 1〜80 文字（バックエンド検証）。isPrivate で PUBLIC/PRIVATE を切り替える。
 */
export async function createChannel(
  workspaceId: string,
  input: { name: string; description?: string; isPrivate: boolean },
): Promise<Channel> {
  const description = input.description?.trim();
  const dto = await api.post<ChannelDto>(
    `/api/workspaces/${Number(workspaceId)}/channels`,
    {
      name: input.name.trim(),
      description: description ? description : undefined,
      type: input.isPrivate ? "PRIVATE" : "PUBLIC",
    },
  );
  return toChannel(dto);
}
