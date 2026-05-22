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
  lastMessagePreview?: string;
  unreadCount: number;
}

export interface Thread {
  parentId: ID;
  replies: Message[];
}
