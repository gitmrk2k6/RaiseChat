export type ID = string;

export interface User {
  id: ID;
  username: string;
  displayName: string;
  avatarColor: string;
  statusMessage?: string;
  email?: string;
  role?: "owner" | "admin" | "member";
}

export interface Workspace {
  id: ID;
  name: string;
  initial: string;
  color: string;
  ownerId: ID;
}

export interface Channel {
  id: ID;
  workspaceId: ID;
  name: string;
  type: "public" | "private";
  topic?: string;
  memberIds: ID[];
  unreadCount: number;
  hasMention: boolean;
}

export interface Reaction {
  emoji: string;
  userIds: ID[];
}

export interface Attachment {
  id: ID;
  type: "image" | "video" | "file";
  url: string;
  name: string;
  sizeBytes: number;
}

export interface Message {
  id: ID;
  channelId?: ID;
  dmRoomId?: ID;
  parentId?: ID;
  authorId: ID;
  // 実 API の Message は投稿者表示名のみ埋め込みで返す（avatarColor は返さない）。
  // ここに displayName と派生 avatarColor を載せておき、描画側は getUser() より優先して使う。
  // mock メッセージでは未設定（getUser(authorId) にフォールバック）。
  author?: { displayName: string; avatarColor: string };
  body: string;
  createdAt: string;
  editedAt?: string;
  reactions: Reaction[];
  attachments: Attachment[];
  mentionIds: ID[];
  threadReplyCount: number;
  threadParticipantIds: ID[];
}

export interface DmRoom {
  id: ID;
  workspaceId: ID;
  memberIds: ID[];
  /**
   * 表示用のメンバー情報（実 API では DmRoom.members を変換して充足）。
   * 相手＝自分以外の判定や名前・アバター色表示に使う。mock 由来のルームでは未設定。
   */
  members?: { id: ID; displayName: string; avatarColor: string }[];
  lastMessagePreview?: string;
  unreadCount: number;
}

export interface Thread {
  parentId: ID;
  replies: Message[];
}
