import type { User } from "@/types";

export const users: User[] = [
  {
    id: "user-1",
    username: "keisuke",
    displayName: "Keisuke Konishi",
    avatarColor: "#4F46E5",
    statusMessage: "🎧 集中モード",
    email: "kkd28mr@gmail.com",
    role: "owner",
  },
  {
    id: "user-2",
    username: "haruka",
    displayName: "Haruka Sato",
    avatarColor: "#0EA5E9",
    statusMessage: "出社中",
    role: "member",
  },
  {
    id: "user-3",
    username: "ryo",
    displayName: "Ryo Yamamoto",
    avatarColor: "#10B981",
    statusMessage: "リモートワーク",
    role: "admin",
  },
  {
    id: "user-4",
    username: "mika",
    displayName: "Mika Tanaka",
    avatarColor: "#F59E0B",
    role: "member",
  },
  {
    id: "user-5",
    username: "kenta",
    displayName: "Kenta Suzuki",
    avatarColor: "#EF4444",
    statusMessage: "🍵 休憩中",
    role: "member",
  },
];

export const currentUserId = "user-1";

export const getUser = (id: string): User =>
  users.find((u) => u.id === id) ?? users[0];
