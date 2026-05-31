// TanStack Query のクエリキーを一元管理する。
// キーの形をここに集約し、コンポーネント間でのキー不一致（キャッシュの取り違え）を防ぐ。

export const queryKeys = {
  workspaces: ["workspaces"] as const,
  workspace: (workspaceId: string) => ["workspaces", workspaceId] as const,
  channels: (workspaceId: string) =>
    ["workspaces", workspaceId, "channels"] as const,
  channel: (channelId: string) => ["channels", channelId] as const,
  channelMessages: (channelId: string) =>
    ["channels", channelId, "messages"] as const,
  dmRooms: (workspaceId: string) =>
    ["workspaces", workspaceId, "dmRooms"] as const,
  dmMessages: (dmRoomId: string) => ["dmRooms", dmRoomId, "messages"] as const,
};
