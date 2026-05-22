import type { Message } from "@/types";

const today = new Date();
const iso = (hoursAgo: number, minutesAgo = 0): string => {
  const d = new Date(today);
  d.setHours(d.getHours() - hoursAgo);
  d.setMinutes(d.getMinutes() - minutesAgo);
  return d.toISOString();
};
const isoDays = (daysAgo: number, hour: number, minute = 0): string => {
  const d = new Date(today);
  d.setDate(d.getDate() - daysAgo);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
};

export const channelMessages: Message[] = [
  // === ch-general ===
  {
    id: "m-g-1",
    channelId: "ch-general",
    authorId: "user-3",
    body: "おはようございます！今週もよろしくお願いします 🙌",
    createdAt: isoDays(1, 9, 12),
    reactions: [{ emoji: "👋", userIds: ["user-1", "user-2", "user-4"] }],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-g-2",
    channelId: "ch-general",
    authorId: "user-2",
    body: "今日の 14:00 から **スプリントレビュー** です。資料はこちら → [Notion](https://example.com)",
    createdAt: isoDays(1, 10, 30),
    reactions: [{ emoji: "👀", userIds: ["user-1"] }],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 2,
    threadParticipantIds: ["user-1", "user-3"],
  },
  {
    id: "m-g-3",
    channelId: "ch-general",
    authorId: "user-1",
    body: "了解です！議事録の準備しておきます ✍️",
    createdAt: isoDays(1, 10, 35),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-g-4",
    channelId: "ch-general",
    authorId: "user-4",
    body: "@keisuke デザインのレビューもお願いできますか？",
    createdAt: iso(2, 15),
    reactions: [],
    attachments: [],
    mentionIds: ["user-1"],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-g-5",
    channelId: "ch-general",
    authorId: "user-1",
    body: "了解です！夕方までに見ます 👍",
    createdAt: iso(2, 0),
    editedAt: iso(1, 50),
    reactions: [{ emoji: "🙏", userIds: ["user-4"] }],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },

  // === ch-random ===
  {
    id: "m-r-1",
    channelId: "ch-random",
    authorId: "user-5",
    body: "近所に新しくできたカフェ、めちゃくちゃ良かったです ☕️\n写真貼っておきます",
    createdAt: iso(5, 20),
    reactions: [
      { emoji: "☕️", userIds: ["user-2", "user-3"] },
      { emoji: "😍", userIds: ["user-4"] },
    ],
    attachments: [
      {
        id: "att-1",
        type: "image",
        url: "https://placehold.co/600x400/8B7355/FFFFFF?text=Cafe+Photo",
        name: "cafe.jpg",
        sizeBytes: 234567,
      },
    ],
    mentionIds: [],
    threadReplyCount: 1,
    threadParticipantIds: ["user-2"],
  },
  {
    id: "m-r-2",
    channelId: "ch-random",
    authorId: "user-2",
    body: "@keisuke ランチ行きませんか？🍜",
    createdAt: iso(1, 30),
    reactions: [],
    attachments: [],
    mentionIds: ["user-1"],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-r-3",
    channelId: "ch-random",
    authorId: "user-3",
    body: "私も行きたい！",
    createdAt: iso(1, 20),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },

  // === ch-dev ===
  {
    id: "m-d-1",
    channelId: "ch-dev",
    authorId: "user-3",
    body: "Spring Boot 3.x の WebSocket 設定、こんな感じで行こうと思います：\n```java\n@Configuration\n@EnableWebSocketMessageBroker\npublic class WebSocketConfig implements WebSocketMessageBrokerConfigurer {\n    @Override\n    public void configureMessageBroker(MessageBrokerRegistry config) {\n        config.enableSimpleBroker(\"/topic\");\n        config.setApplicationDestinationPrefixes(\"/app\");\n    }\n}\n```",
    createdAt: iso(8),
    reactions: [{ emoji: "🚀", userIds: ["user-1", "user-2"] }],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 3,
    threadParticipantIds: ["user-1", "user-2"],
  },
  {
    id: "m-d-2",
    channelId: "ch-dev",
    authorId: "user-1",
    body: "良さそうですね！後で Redis Pub/Sub と組み合わせる方針も検討しましょう",
    createdAt: iso(7, 30),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-d-3",
    channelId: "ch-dev",
    authorId: "user-2",
    body: "DB スキーマも今日中に Draft 出します",
    createdAt: iso(4),
    reactions: [{ emoji: "👍", userIds: ["user-1", "user-3"] }],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },

  // === ch-secret ===
  {
    id: "m-s-1",
    channelId: "ch-secret",
    authorId: "user-3",
    body: "新規プロジェクトの方針、ドキュメントにまとめました 📄",
    createdAt: iso(3),
    reactions: [],
    attachments: [
      {
        id: "att-2",
        type: "file",
        url: "#",
        name: "project-spec.pdf",
        sizeBytes: 1234567,
      },
    ],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },

  // === ch-design ===
  {
    id: "m-de-1",
    channelId: "ch-design",
    authorId: "user-4",
    body: "新しいロゴ案、3パターン作りました。意見聞かせてください 🎨",
    createdAt: iso(6),
    reactions: [
      { emoji: "🎨", userIds: ["user-1"] },
      { emoji: "❤️", userIds: ["user-1"] },
    ],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
];

export const dmMessages: Message[] = [
  {
    id: "m-dm1-1",
    dmRoomId: "dm-1",
    authorId: "user-2",
    body: "お疲れさまです！明日のレビュー、よろしくお願いします",
    createdAt: iso(2),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-dm1-2",
    dmRoomId: "dm-1",
    authorId: "user-1",
    body: "承知しました 👍 資料事前に共有しますね",
    createdAt: iso(2, -10),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-dm1-3",
    dmRoomId: "dm-1",
    authorId: "user-2",
    body: "助かります！\n明日のレビュー、よろしくお願いします！",
    createdAt: iso(0, 30),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-dm2-1",
    dmRoomId: "dm-2",
    authorId: "user-1",
    body: "PR #5 出しました。お時間あるときレビューお願いします 🙏",
    createdAt: iso(4),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
  {
    id: "m-dm2-2",
    dmRoomId: "dm-2",
    authorId: "user-3",
    body: "OK、PRレビューしておきます 👍",
    createdAt: iso(3, 30),
    reactions: [],
    attachments: [],
    mentionIds: [],
    threadReplyCount: 0,
    threadParticipantIds: [],
  },
];

export const threadReplies: Record<string, Message[]> = {
  "m-g-2": [
    {
      id: "m-g-2-r1",
      channelId: "ch-general",
      parentId: "m-g-2",
      authorId: "user-1",
      body: "資料、確認しました！1点質問あります",
      createdAt: isoDays(1, 11, 0),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
    {
      id: "m-g-2-r2",
      channelId: "ch-general",
      parentId: "m-g-2",
      authorId: "user-3",
      body: "どうぞ！",
      createdAt: isoDays(1, 11, 2),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
  ],
  "m-r-1": [
    {
      id: "m-r-1-r1",
      channelId: "ch-random",
      parentId: "m-r-1",
      authorId: "user-2",
      body: "場所教えてください！今度行きたい！",
      createdAt: iso(4),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
  ],
  "m-d-1": [
    {
      id: "m-d-1-r1",
      channelId: "ch-dev",
      parentId: "m-d-1",
      authorId: "user-1",
      body: "`enableSimpleBroker` ではなく STOMP broker relay にしなくて大丈夫ですか？",
      createdAt: iso(7, 50),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
    {
      id: "m-d-1-r2",
      channelId: "ch-dev",
      parentId: "m-d-1",
      authorId: "user-3",
      body: "MVP段階では simple broker で十分かと。スケール時に切り替え検討しましょう",
      createdAt: iso(7, 45),
      reactions: [{ emoji: "👍", userIds: ["user-1"] }],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
    {
      id: "m-d-1-r3",
      channelId: "ch-dev",
      parentId: "m-d-1",
      authorId: "user-2",
      body: "了解です！",
      createdAt: iso(7, 30),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    },
  ],
};

export const getChannelMessages = (channelId: string): Message[] =>
  channelMessages
    .filter((m) => m.channelId === channelId)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

export const getDmMessages = (dmRoomId: string): Message[] =>
  dmMessages
    .filter((m) => m.dmRoomId === dmRoomId)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

export const getThreadReplies = (parentId: string): Message[] =>
  (threadReplies[parentId] ?? []).slice().sort((a, b) => a.createdAt.localeCompare(b.createdAt));

export const getAllMessages = (): Message[] => [
  ...channelMessages,
  ...dmMessages,
  ...Object.values(threadReplies).flat(),
];
