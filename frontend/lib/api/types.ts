// バックエンド DTO に対応する共有型。
// バックエンドは JSON を camelCase で返す（docs/api-design.md §1 原則3）ためそのまま写す。
// 将来的には OpenAPI からの自動生成も検討するが、MVP では手書きで管理する。

/** POST /api/auth/login, /signup, /refresh のレスポンス（TokenResponse）。 */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  /** アクセストークンの有効期間（秒）。 */
  expiresIn: number;
}

/** POST /api/auth/login のリクエスト。識別子は email ではなく userId。 */
export interface LoginRequest {
  userId: string;
  password: string;
}

/** POST /api/auth/signup のリクエスト。 */
export interface SignupRequest {
  userId: string;
  displayName: string;
  password: string;
}

/** GET /api/auth/me のレスポンス（MeResponse）。id は数値（BIGINT）。 */
export interface MeResponse {
  id: number;
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  statusMessage: string | null;
}

/** API が埋め込む User サブセット（DmRoom.members など）。avatarColor は返らない。 */
export interface UserDto {
  id: number;
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  statusMessage: string | null;
}

/**
 * GET /api/workspaces/{wsId}/dm/rooms の要素（DmRoom）。
 * members は 1 対 1 DM なので常に 2 件。lastMessagePreview / unreadCount は返らない。
 */
export interface DmRoomDto {
  id: number;
  workspaceId: number;
  members: UserDto[];
  createdAt: string;
}

/** 一覧系の共通ページングレスポンス（docs/api-design.md §1.2）。 */
export interface Page<T> {
  items: T[];
  /** 次ページの不透明カーソル。次ページがなければ null。 */
  nextCursor: string | null;
  hasMore: boolean;
}

/** GET /api/workspaces のレスポンス要素（Workspace）。id は数値（BIGINT）。 */
export interface WorkspaceDto {
  id: number;
  name: string;
  description: string | null;
  ownerUserId: number;
  createdAt: string;
}

/** GET /api/workspaces/{wsId}/channels のレスポンス要素（Channel）。id は数値（BIGINT）。 */
export interface ChannelDto {
  id: number;
  workspaceId: number;
  name: string;
  description: string | null;
  type: "PUBLIC" | "PRIVATE";
  createdByUserId: number;
  createdAt: string;
}

/** メッセージ添付（AttachmentResponse）。S3 キーは晒さず復元済み URL のみ返る。 */
export interface AttachmentDto {
  id: number;
  messageId: number;
  uploaderId: number;
  url: string;
  mimeType: string;
  sizeBytes: number;
  originalFilename: string;
  width: number | null;
  height: number | null;
  durationSec: number | null;
  createdAt: string;
}

/**
 * GET /api/channels/{id}/messages・/api/dm/rooms/{id}/messages の要素（MessageResponse）。
 * 投稿者は authorId / authorUserId / authorDisplayName にフラット展開され、avatarUrl・avatarColor は返らない。
 * reactions / threadReplyCount はこのレスポンスに含まれない（WS / 別 API 経由）。
 */
export interface MessageDto {
  id: number;
  channelId: number | null;
  dmRoomId: number | null;
  authorId: number;
  authorUserId: string;
  authorDisplayName: string;
  body: string;
  parentMessageId: number | null;
  mentionedUserIds: number[];
  attachments: AttachmentDto[];
  editedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
