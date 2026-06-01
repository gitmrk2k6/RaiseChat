// メッセージ全文検索の API 呼び出し（F-13）。
// DTO（id は数値 BIGINT、投稿者はフラット展開、avatarColor 非返却）をフロントの表示型 SearchHit へ変換する。
// 既存の messages.ts / dm.ts と同方針で、ID number↔string 変換と表示専用フィールド補完を
// この lib/api 層に封じ込める（avatarColor は authorId から決定論的に導出）。

import { api } from "./client";
import { avatarColorFor } from "./messages";
import type { Page, SearchResultItemDto } from "./types";
import type { ID } from "@/types";

/**
 * 検索結果 1 件の表示用モデル。
 * channelId / channelName は channel ヒット時のみ（DM ヒットでは undefined）、
 * dmRoomId は DM ヒット時のみセットされる。相手名は検索 API に含まれないため
 * 画面側で DM ルーム一覧（listDmRooms）から解決する。
 */
export interface SearchHit {
  messageId: ID;
  channelId?: ID;
  channelName?: string;
  dmRoomId?: ID;
  parentId?: ID;
  authorId: ID;
  author: { displayName: string; avatarColor: string };
  body: string;
  createdAt: string;
  editedAt?: string;
}

/** SearchResultItemDto → 表示用 SearchHit。id を string 化し、avatarColor を補完する。 */
export function toSearchHit(dto: SearchResultItemDto): SearchHit {
  const authorId = String(dto.authorId);
  return {
    messageId: String(dto.messageId),
    channelId: dto.channelId != null ? String(dto.channelId) : undefined,
    channelName: dto.channelName ?? undefined,
    dmRoomId: dto.dmRoomId != null ? String(dto.dmRoomId) : undefined,
    parentId: dto.parentMessageId != null ? String(dto.parentMessageId) : undefined,
    authorId,
    author: {
      displayName: dto.authorDisplayName,
      avatarColor: avatarColorFor(authorId),
    },
    body: dto.body,
    createdAt: dto.createdAt,
    editedAt: dto.editedAt ?? undefined,
  };
}

/**
 * GET /api/workspaces/{wsId}/search ワークスペース内のメッセージ全文検索（F-13）。
 * 結果は id 降順・cursor ページング（既存メッセージ履歴と同方式）。
 * アクセス制御はバックエンド側（参加チャンネル / 当事者 DM のみ）で完結する。
 * q は必須（空文字を渡すとバックエンドが 400 を返す）ため、呼び出し側で空チェックしてから使う。
 */
export async function listSearchResults(
  workspaceId: string,
  q: string,
  cursor?: string | null,
  limit?: number,
): Promise<Page<SearchHit>> {
  const params = new URLSearchParams();
  params.set("q", q);
  if (cursor) params.set("cursor", cursor);
  if (limit != null) params.set("limit", String(limit));
  const page = await api.get<Page<SearchResultItemDto>>(
    `/api/workspaces/${Number(workspaceId)}/search?${params.toString()}`,
  );
  return {
    items: page.items.map(toSearchHit),
    nextCursor: page.nextCursor,
    hasMore: page.hasMore,
  };
}
