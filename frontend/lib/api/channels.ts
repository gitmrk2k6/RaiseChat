// チャンネル関連の API 呼び出し。
// DTO（id は数値 BIGINT, type は大文字）をフロントの ViewModel（id は string, type は小文字）へ変換する。
// 一覧 API に含まれないフィールド（topic 以外のメタ）はプレースホルダで補完する:
//  - topic    ← API の description
//  - unreadCount / hasMention → 0 / false（型を満たすための既定値。実値は描画時に
//    NotificationProvider(useUnread) が scope 単位で上書きする。F-14 接続済み）
//  - memberIds → []（一覧 API に含まれない）

import { api } from "./client";
import { avatarColorFor } from "./messages";
import type { ChannelDto, ChannelMemberDto, Page } from "./types";
import type { Channel, WorkspaceMember } from "@/types";

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

/**
 * POST /api/channels/{id}/members 選択したメンバーをチャンネルへ直接追加する。
 * 既にメンバーの場合や過去に退出した場合もバックエンドが冪等に処理する。
 */
export async function addChannelMembers(
  channelId: string,
  userIds: string[],
): Promise<Channel> {
  const dto = await api.post<ChannelDto>(
    `/api/channels/${Number(channelId)}/members`,
    { userIds: userIds.map(Number) },
  );
  return toChannel(dto);
}

/**
 * GET /api/channels/{id}/members チャンネルのアクティブメンバー一覧。
 * 表示用に avatarColor を id から決定論的に補完する（role はチャンネルでは無いため "MEMBER" 固定）。
 */
export async function getChannelMembers(channelId: string): Promise<WorkspaceMember[]> {
  const dtos = await api.get<ChannelMemberDto[]>(`/api/channels/${Number(channelId)}/members`);
  return dtos.map((m) => ({
    id: String(m.id),
    userId: m.userId,
    displayName: m.displayName,
    avatarColor: avatarColorFor(String(m.id)),
    role: "MEMBER" as const,
  }));
}

/** DELETE /api/channels/{id}/members/{userId} メンバーをチャンネルから除外（キック）する。 */
export async function removeChannelMember(channelId: string, userId: string): Promise<void> {
  await api.delete<void>(`/api/channels/${Number(channelId)}/members/${Number(userId)}`);
}
