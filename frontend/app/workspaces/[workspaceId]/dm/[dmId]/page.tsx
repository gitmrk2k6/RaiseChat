"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { notFound, useSearchParams } from "next/navigation";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { listDmRooms } from "@/lib/api/dm";
import {
  addReaction,
  deleteMessage,
  editMessage,
  listDmMessages,
  removeReaction,
} from "@/lib/api/messages";
import { markDmRead } from "@/lib/api/notifications";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { useUnread } from "@/lib/notifications/NotificationContext";
import { useStompSubscription } from "@/lib/ws/useStompSubscription";
import {
  applyEdited,
  applyReaction,
  deletedMessageId,
  messageCreatedToMessage,
  parseWsEvent,
  publishDmMessage,
  publishDmTyping,
} from "@/lib/ws/messages";
import { useTypingNotifier } from "@/lib/ws/useTypingNotifier";
import { useTypingIndicator } from "@/lib/ws/useTypingIndicator";
import { MessageList } from "@/components/chat/MessageList";
import { MessageInput } from "@/components/chat/MessageInput";
import { TypingIndicator } from "@/components/chat/TypingIndicator";
import { Avatar } from "@/components/ui/Avatar";
import type { Message } from "@/types";

export default function DmPage({
  params,
}: {
  params: { workspaceId: string; dmId: string };
}) {
  const { user } = useAuth();
  const meId = user ? String(user.id) : null;
  const { getDmUnread } = useUnread();
  const dmUnread = getDmUnread(params.dmId).unread;

  // 検索ジャンプ対象（?msg=<id>）。該当が履歴の奥にある場合は読み込まれるまで古い方向へ自動ページングする。
  const searchParams = useSearchParams();
  const jumpMessageId = searchParams.get("msg");

  // この DM を開いている間は既読を維持する（チャンネルと同方針）。
  //  - 開いた時、および表示中に未読が増えるたびに最新まで既読化（POST /api/dm/rooms/{id}/read, body 無し＝最新）
  //  - 既に未読 0 なら何もしない。確定（バッジ 0 化）はサーバ発 WS 通知に委ねる。
  useEffect(() => {
    if (dmUnread === 0) return;
    void markDmRead(params.dmId).catch(() => {});
  }, [params.dmId, dmUnread]);

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

  // トップレベルメッセージの送受信・編集・削除・リアクションはすべて実 API ＋ WS（/topic/dm/{roomId}）。
  // 受信は WsEvent を type で振り分けて messages へ反映する（チャンネルページと同方針）:
  //  - MESSAGE_CREATED: 未保有 id のみ追加（自分の送信も echo で届く）
  //  - MESSAGE_EDITED / MESSAGE_DELETED / REACTION_* も WS 受信で state を更新
  // 書き込みは REST に投げるだけで、確定はすべて WS 受信に委ねる（楽観更新なし）。
  const [messages, setMessages] = useState<Message[]>([]);
  const deletedIdsRef = useRef<Set<string>>(new Set());

  // 履歴・WS 受信の双方を同じ id マージで取り込む共通インサータ。
  const mergeMessages = (incoming: Message[]) => {
    setMessages((prev) => {
      const existing = new Set(prev.map((m) => m.id));
      const additions = incoming.filter(
        (m) => !existing.has(m.id) && !deletedIdsRef.current.has(m.id),
      );
      if (additions.length === 0) return prev;
      return [...additions, ...prev].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    });
  };

  useEffect(() => {
    if (historyAsc.length === 0) return;
    mergeMessages(historyAsc);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [historyAsc]);

  // 検索ジャンプ: 対象 id がまだ読み込まれていなければ、見つかるまで古い履歴を自動ロードする（上限ガードあり）。
  const jumpAttemptsRef = useRef(0);
  useEffect(() => {
    if (!jumpMessageId) return;
    if (messages.some((m) => m.id === jumpMessageId)) return; // 既に表示可能
    if (!hasNextPage || isFetchingNextPage) return;
    if (jumpAttemptsRef.current >= 10) return;
    jumpAttemptsRef.current += 1;
    void fetchNextPage();
  }, [jumpMessageId, messages, hasNextPage, isFetchingNextPage, fetchNextPage]);

  // リアルタイム受信。event.type で振り分けて messages へ反映する。
  useStompSubscription(`/topic/dm/${params.dmId}`, (frame) => {
    const event = parseWsEvent(frame.body);
    if (!event) return;
    switch (event.type) {
      case "MESSAGE_CREATED": {
        const created = messageCreatedToMessage(event);
        if (created) mergeMessages([created]);
        break;
      }
      case "MESSAGE_EDITED":
        setMessages((prev) => applyEdited(prev, event));
        break;
      case "MESSAGE_DELETED": {
        const id = deletedMessageId(event);
        if (id) {
          deletedIdsRef.current.add(id);
          setMessages((prev) => prev.filter((m) => m.id !== id));
        }
        break;
      }
      case "REACTION_ADDED":
      case "REACTION_REMOVED":
        setMessages((prev) => applyReaction(prev, event));
        break;
    }
  });

  const send = (body: string) => {
    publishDmMessage(params.dmId, body);
  };

  // typing: 入力中の送信（throttle）と、相手の「入力中…」受信。
  const notifyTyping = useTypingNotifier(() => publishDmTyping(params.dmId));
  const typers = useTypingIndicator(`/topic/dm/${params.dmId}/typing`, meId);

  // 編集/削除/リアクションは REST に投げるだけ。state 反映は WS 受信で行う（楽観更新なし）。
  const toggleReact = (id: string, emoji: string) => {
    if (!meId) return;
    const target = messages.find((m) => m.id === id);
    const mine = target?.reactions.find((r) => r.emoji === emoji)?.userIds.includes(meId) ?? false;
    void (mine ? removeReaction(id, emoji) : addReaction(id, emoji));
  };

  const edit = (id: string, body: string) => {
    void editMessage(id, body);
  };

  const remove = (id: string) => {
    void deleteMessage(id);
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
          highlightMessageId={jumpMessageId}
        />
      )}
      <TypingIndicator typers={typers} />
      <MessageInput
        placeholder={`${partnerName} へのメッセージ`}
        onSend={send}
        onTyping={notifyTyping}
      />
    </div>
  );
}
