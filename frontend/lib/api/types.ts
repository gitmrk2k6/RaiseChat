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

/** ワークスペースメンバー（WorkspaceMemberResponse）。id は user の数値 ID。 */
export interface WorkspaceMemberDto {
  id: number;
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  role: "OWNER" | "MEMBER";
}

/** GET /api/workspaces/{wsId} 詳細（WorkspaceDetailResponse）。一覧の WorkspaceDto に members を加えたもの。 */
export interface WorkspaceDetailDto extends WorkspaceDto {
  members: WorkspaceMemberDto[];
}

/** GET /api/channels/{id}/members の要素（ChannelMemberResponse）。チャンネルメンバーにロールは無い。 */
export interface ChannelMemberDto {
  id: number;
  userId: string;
  displayName: string;
  avatarUrl: string | null;
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

/**
 * リアクション集計（ReactionResponse）。1 メッセージ・1 emoji の合計。
 * REST（POST /api/messages/{id}/reactions）レスポンスと WS の REACTION_ADDED/REMOVED payload が同形。
 * userIds は付与したユーザー id（数値・古い順）。count は冗長だが配信時点の合計を 1 値で持つ。
 */
export interface ReactionDto {
  messageId: number;
  emoji: string;
  count: number;
  userIds: number[];
}

/**
 * GET /api/workspaces/{wsId}/search の結果要素（SearchResultItem）。
 * 投稿者は authorId / authorUserId / authorDisplayName にフラット展開され、avatarColor は返らない。
 * DM ヒットの場合 channelId / channelName は null（dmRoomId のみ）。
 * レスポンス全体は { items, nextCursor, hasMore } で一覧系と同形なので Page<SearchResultItemDto> を使う。
 */
export interface SearchResultItemDto {
  messageId: number;
  channelId: number | null;
  channelName: string | null;
  dmRoomId: number | null;
  parentMessageId: number | null;
  authorId: number;
  authorUserId: string;
  authorDisplayName: string;
  body: string;
  editedAt: string | null;
  createdAt: string;
}

/**
 * GET /api/notifications/unread の結果要素（UnreadItem）。1 スコープ分の未読数（F-14）。
 * scopeType は "channel" | "dm"、mentionCount は unreadCount の内数。未読 0 のスコープは返らない。
 */
export interface UnreadItemDto {
  scopeType: "channel" | "dm";
  scopeId: number;
  unreadCount: number;
  mentionCount: number;
}

/** GET /api/notifications/unread のレスポンス（UnreadResponse）。 */
export interface UnreadResponseDto {
  items: UnreadItemDto[];
}

/**
 * WS /user/queue/notifications で配信されるユーザー個別イベント（NotificationEvent）。
 *
 * - 未読系（UNREAD_UPDATED / UNREAD_CLEARED）: 各 scope の更新後「絶対値」を載せた自己完結イベント。
 *   scopeType は "channel" | "dm"。NotificationContext が数を上書きする。
 * - 剥奪系（WORKSPACE_REMOVED / CHANNEL_REMOVED, F-16）: 対象 WS / チャンネルから外れた通知。
 *   scopeType は "workspace" | "channel"、unreadCount / mentionCount は 0。MembershipListener が
 *   キャッシュ無効化・リダイレクトに用いる。
 * - 追加系（CHANNEL_ADDED）: チャンネルへ直接追加された通知（CHANNEL_REMOVED と対称）。
 *   scopeType は "channel"、unreadCount / mentionCount は 0。MembershipListener が
 *   キャッシュ無効化（サイドバー反映）に用いる。
 */
export interface NotificationEventDto {
  type:
    | "UNREAD_UPDATED"
    | "UNREAD_CLEARED"
    | "WORKSPACE_REMOVED"
    | "CHANNEL_REMOVED"
    | "CHANNEL_ADDED";
  scopeType: "channel" | "dm" | "workspace";
  scopeId: number;
  unreadCount: number;
  mentionCount: number;
}

/** GET /api/presence のレスポンス。現在オンラインなユーザーの数値 id 一覧（seed 用）。 */
export interface PresenceResponseDto {
  userIds: number[];
}

/**
 * WS /topic/presence で配信される presence 変化イベント（PresenceEvent）。
 * userId は数値 id（自分判定・突き合わせに使う）、online=true でオンライン / false でオフライン。
 */
export interface PresenceEventDto {
  userId: number;
  online: boolean;
}

/** PATCH /api/messages/{id} のリクエストボディ（EditMessageRequest）。 */
export interface EditMessageRequest {
  body: string;
}

/** POST /api/messages/{parentId}/replies のリクエストボディ（ReplyMessageRequest）。 */
export interface ReplyMessageRequest {
  body: string;
}
