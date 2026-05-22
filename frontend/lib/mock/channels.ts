import type { Channel } from "@/types";

export const channels: Channel[] = [
  {
    id: "ch-general",
    workspaceId: "ws-1",
    name: "general",
    type: "public",
    topic: "全員参加の総合チャンネルです 👋",
    memberIds: ["user-1", "user-2", "user-3", "user-4", "user-5"],
    unreadCount: 0,
    hasMention: false,
  },
  {
    id: "ch-random",
    workspaceId: "ws-1",
    name: "random",
    type: "public",
    topic: "雑談やランチの話など、お気軽にどうぞ ☕️",
    memberIds: ["user-1", "user-2", "user-3", "user-4", "user-5"],
    unreadCount: 3,
    hasMention: true,
  },
  {
    id: "ch-dev",
    workspaceId: "ws-1",
    name: "dev-backend",
    type: "public",
    topic: "Spring Boot / Java / DB 関連の話題",
    memberIds: ["user-1", "user-2", "user-3"],
    unreadCount: 12,
    hasMention: false,
  },
  {
    id: "ch-secret",
    workspaceId: "ws-1",
    name: "secret-pj",
    type: "private",
    topic: "新規プロジェクト用 🔒",
    memberIds: ["user-1", "user-3"],
    unreadCount: 1,
    hasMention: false,
  },
  {
    id: "ch-design",
    workspaceId: "ws-1",
    name: "design",
    type: "public",
    topic: "Figma / UI / UX 関連",
    memberIds: ["user-1", "user-4"],
    unreadCount: 0,
    hasMention: false,
  },
];

export const getChannel = (id: string): Channel | undefined =>
  channels.find((c) => c.id === id);

export const getChannelsByWorkspace = (workspaceId: string): Channel[] =>
  channels.filter((c) => c.workspaceId === workspaceId);
