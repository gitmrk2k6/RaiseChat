// メッセージ履歴関連の API 呼び出し（F-05）。
// DTO（id は数値 BIGINT、投稿者はフラット展開、avatarColor 非返却）をフロントの Message へ変換する。
// API が返さないフィールドはプレースホルダで補完する（2単位目の unread 系と同方針）:
//  - reactions            → []   （リアクションは WS / 別 API 経由。履歴 GET には含まれない）
//  - threadReplyCount     → 0    （API 非返却）
//  - threadParticipantIds → []   （API 非返却）
//  - author.avatarColor   → authorId から決定論的に導出（後述 avatarColorFor）

import { api } from "./client";
import type { AttachmentDto, MessageDto, Page } from "./types";
import type { Attachment, Message } from "@/types";

// mock の配色（lib/mock/users.ts）と同系統のパレット。authorId から決定論的に 1 色選ぶ。
const AVATAR_PALETTE = [
  "#4F46E5",
  "#0EA5E9",
  "#10B981",
  "#F59E0B",
  "#EF4444",
  "#8B5CF6",
  "#EC4899",
  "#14B8A6",
];

/** 数値文字列の authorId を安定ハッシュしてパレットの 1 色に割り当てる。 */
export function avatarColorFor(key: string): string {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) | 0;
  }
  return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
}

/** mimeType から表示用の添付種別を判定する。 */
function attachmentType(mimeType: string): Attachment["type"] {
  if (mimeType.startsWith("image/")) return "image";
  if (mimeType.startsWith("video/")) return "video";
  return "file";
}

/** AttachmentDto → フロントの Attachment。 */
function toAttachment(dto: AttachmentDto): Attachment {
  return {
    id: String(dto.id),
    type: attachmentType(dto.mimeType),
    url: dto.url,
    name: dto.originalFilename,
    sizeBytes: dto.sizeBytes,
  };
}

/** MessageDto → フロントの Message。id を string 化し、API 非返却フィールドはプレースホルダ補完。 */
export function toMessage(dto: MessageDto): Message {
  const authorId = String(dto.authorId);
  return {
    id: String(dto.id),
    channelId: dto.channelId != null ? String(dto.channelId) : undefined,
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
    reactions: [],
    attachments: dto.attachments.map(toAttachment),
    mentionIds: dto.mentionedUserIds.map(String),
    threadReplyCount: 0,
    threadParticipantIds: [],
  };
}

/**
 * GET /api/channels/{id}/messages チャンネルのメッセージ履歴（createdAt 降順、cursor ページング）。
 * nextCursor は不透明文字列としてそのまま次リクエストの cursor に渡す。
 */
export async function listChannelMessages(
  channelId: string,
  cursor?: string | null,
  limit?: number,
): Promise<Page<Message>> {
  const params = new URLSearchParams();
  if (cursor) params.set("cursor", cursor);
  if (limit != null) params.set("limit", String(limit));
  const qs = params.toString();
  const page = await api.get<Page<MessageDto>>(
    `/api/channels/${Number(channelId)}/messages${qs ? `?${qs}` : ""}`,
  );
  return {
    items: page.items.map(toMessage),
    nextCursor: page.nextCursor,
    hasMore: page.hasMore,
  };
}
