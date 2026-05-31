"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { notFound } from "next/navigation";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { listDmRooms } from "@/lib/api/dm";
import { listDmMessages } from "@/lib/api/messages";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { MessageList } from "@/components/chat/MessageList";
import { MessageInput } from "@/components/chat/MessageInput";
import { Avatar } from "@/components/ui/Avatar";
import type { Message } from "@/types";

export default function DmPage({
  params,
}: {
  params: { workspaceId: string; dmId: string };
}) {
  const { user } = useAuth();
  const meId = user ? String(user.id) : null;

  // 単体ルーム取得 API は無いため、ルーム一覧（Sidebar と同 queryKey）から id で引く。
  const { data: rooms, isLoading: roomsLoading } = useQuery({
    queryKey: queryKeys.dmRooms(params.workspaceId),
    queryFn: () => listDmRooms(params.workspaceId),
    enabled: !!params.workspaceId,
  });
  const room = rooms?.find((r) => r.id === params.dmId);
  const partner = room?.members?.find((m) => m.id !== meId) ?? room?.members?.[0];

  // メッセージ履歴（実 API、cursor 無限スクロール）。API は createdAt 降順で返すので表示は昇順へ。
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading: messagesLoading,
  } = useInfiniteQuery({
    queryKey: queryKeys.dmMessages(params.dmId),
    queryFn: ({ pageParam }) => listDmMessages(params.dmId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor : undefined),
    refetchOnWindowFocus: false,
    staleTime: Infinity,
  });

  // pages: [新しいページ(降順), さらに古いページ(降順), ...] → 全体を昇順（古→新）に整える。
  const historyAsc = useMemo(() => {
    if (!data) return [];
    return [...data.pages.flatMap((p) => p.items)].reverse();
  }, [data]);

  // 書き込み系（送信/編集/削除/リアクション）は今単位では mock 据え置き（統合3 と同方針）。
  // 実 API 履歴は messages へ id マージで合流させ、ローカル変更を壊さない。
  const [messages, setMessages] = useState<Message[]>([]);
  const deletedIdsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (historyAsc.length === 0) return;
    setMessages((prev) => {
      const existing = new Set(prev.map((m) => m.id));
      const additions = historyAsc.filter(
        (m) => !existing.has(m.id) && !deletedIdsRef.current.has(m.id),
      );
      if (additions.length === 0) return prev;
      return [...additions, ...prev].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    });
  }, [historyAsc]);

  const send = (body: string) => {
    if (!meId) return;
    const m: Message = {
      id: `m-local-${Date.now()}`,
      dmRoomId: params.dmId,
      authorId: meId,
      body,
      createdAt: new Date().toISOString(),
      reactions: [],
      attachments: [],
      mentionIds: [],
      threadReplyCount: 0,
      threadParticipantIds: [],
    };
    setMessages((prev) => [...prev, m]);
  };

  const toggleReact = (id: string, emoji: string) => {
    if (!meId) return;
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id !== id) return m;
        const existing = m.reactions.find((r) => r.emoji === emoji);
        if (!existing) {
          return { ...m, reactions: [...m.reactions, { emoji, userIds: [meId] }] };
        }
        const mine = existing.userIds.includes(meId);
        const nextIds = mine
          ? existing.userIds.filter((u) => u !== meId)
          : [...existing.userIds, meId];
        return {
          ...m,
          reactions: m.reactions
            .map((r) => (r.emoji === emoji ? { ...r, userIds: nextIds } : r))
            .filter((r) => r.userIds.length > 0),
        };
      }),
    );
  };

  const edit = (id: string, body: string) =>
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, body, editedAt: new Date().toISOString() } : m)),
    );

  const remove = (id: string) => {
    deletedIdsRef.current.add(id);
    setMessages((prev) => prev.filter((m) => m.id !== id));
  };

  if (!room) {
    if (roomsLoading) {
      return (
        <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
          読み込み中…
        </div>
      );
    }
    notFound();
  }

  const partnerName = partner?.displayName ?? "(不明)";

  return (
    <div className="flex-1 flex flex-col min-w-0">
      <div className="h-14 px-5 flex items-center gap-3 border-b border-gray-200 bg-white shrink-0">
        <Avatar name={partnerName} color={partner?.avatarColor ?? "#6B7280"} size="sm" />
        <div>
          <div className="font-bold text-gray-900 text-[15px]">{partnerName}</div>
          <div className="text-xs text-gray-500 flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            アクティブ
          </div>
        </div>
      </div>
      {messagesLoading ? (
        <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
          読み込み中…
        </div>
      ) : (
        <MessageList
          messages={messages}
          onReact={toggleReact}
          onEdit={edit}
          onDelete={remove}
          onLoadOlder={() => fetchNextPage()}
          hasMore={hasNextPage}
          loadingOlder={isFetchingNextPage}
        />
      )}
      <MessageInput placeholder={`${partnerName} へのメッセージ`} onSend={send} />
    </div>
  );
}
