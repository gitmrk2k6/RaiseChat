import type { DmRoom } from "@/types";

export const dmRooms: DmRoom[] = [
  {
    id: "dm-1",
    workspaceId: "ws-1",
    memberIds: ["user-1", "user-2"],
    lastMessagePreview: "明日のレビュー、よろしくお願いします！",
    unreadCount: 2,
  },
  {
    id: "dm-2",
    workspaceId: "ws-1",
    memberIds: ["user-1", "user-3"],
    lastMessagePreview: "OK、PRレビューしておきます 👍",
    unreadCount: 0,
  },
];

export const getDmRoom = (id: string): DmRoom | undefined =>
  dmRooms.find((d) => d.id === id);

export const getDmsByWorkspace = (workspaceId: string): DmRoom[] =>
  dmRooms.filter((d) => d.workspaceId === workspaceId);
