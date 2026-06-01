"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { notFound } from "next/navigation";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { getChannel as getMockChannel } from "@/lib/mock/channels";
import { getChannel as fetchChannel } from "@/lib/api/channels";
import {
  addReaction,
  createReply,
  deleteMessage,
  editMessage,
  listChannelMessages,
  listReplies,
  removeReaction,
} from "@/lib/api/messages";
import { markChannelRead } from "@/lib/api/notifications";
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
  publishChannelMessage,
} from "@/lib/ws/messages";
import { ChannelHeader } from "@/components/chat/ChannelHeader";
import { MessageList } from "@/components/chat/MessageList";
import { MessageInput } from "@/components/chat/MessageInput";
import { ThreadPanel } from "@/components/chat/ThreadPanel";
import type { Message } from "@/types";

export default function ChannelPage({ params }: { params: { channelId: string } }) {
  const { user } = useAuth();
  const meId = user ? String(user.id) : null;
  const { getChannelUnread } = useUnread();
  const channelUnread = getChannelUnread(params.channelId).unread;

  // このチャンネルを開いている間は既読を維持する。
  //  - 開いた時、および表示中に未読が増えるたびに最新まで既読化（POST /api/channels/{id}/read, body 無し＝最新）
  //  - 既に未読 0 なら何もしない（無駄な再既読 POST を避ける）
  // 確定（バッジ 0 化）はサーバ発の WS 通知（/user/queue/notifications → NotificationProvider）に委ねる。
  useEffect(() => {
    if (channelUnread === 0) return;
    void markChannelRead(params.channelId).catch(() => {});
  }, [params.channelId, channelUnread]);

  // チャンネルのメタは mock を優先。mock に無い（＝実 API のチャンネル）場合はヘッダ用に実 API から取得する。
  const mockChannel = getMockChannel(params.channelId);
  const { data: apiChannel, isLoading: channelLoading } = useQuery({
    queryKey: queryKeys.channel(params.channelId),
    queryFn: () => fetchChannel(params.channelId),
    enabled: !mockChannel,
  });
  const channel = mockChannel ?? apiChannel;

  // メッセージ履歴（実 API、cursor 無限スクロール）。API は createdAt 降順で返すので表示は昇順へ並べ替える。
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading: messagesLoading,
  } = useInfiniteQuery({
    queryKey: queryKeys.channelMessages(params.channelId),
    queryFn: ({ pageParam }) => listChannelMessages(params.channelId, pageParam),
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

  // トップレベルメッセージの送受信・編集・削除・リアクションはすべて実 API ＋ WS。
  // 受信は /topic/channels/{id} の WsEvent を type で振り分けて messages へ反映する:
  //  - MESSAGE_CREATED: 未保有 id のみ追加（既存は上書きしない／ローカル削除済み id は再追加しない）
  //  - MESSAGE_EDITED : 該当 id の body/editedAt 等を更新（reactions は潰さず保持）
  //  - MESSAGE_DELETED: deletedIdsRef に積んで除去
  //  - REACTION_*     : emoji 単位の集計を上書き（セット型なので自己 echo でも冪等）
  // 書き込みは REST に投げるだけで、確定はすべて WS 受信に委ねる（楽観更新なし）。
  const [messages, setMessages] = useState<Message[]>([]);
  const deletedIdsRef = useRef<Set<string>>(new Set());

  // スレッド返信も実 API ＋ WS。開いている 1 スレッドぶんだけを単一配列で持つ（閉じたら破棄）。
  //  - 履歴: GET /api/messages/{parentId}/replies（id 昇順）で開いたときに初期ロード
  //  - 受信: /topic/threads/{parentId} を開いている間だけ購読し、トップレベルと同じ純関数で反映
  //  - 投稿/編集/削除/リアクション: REST に投げるだけ。確定はすべて thread WS 受信で
  // 親(root)自身の編集/リアクション/削除は channel トピック → 既存 messages 経路で parent に反映される。
  const [threadParentId, setThreadParentId] = useState<string | null>(null);
  const [replies, setReplies] = useState<Message[]>([]);
  const deletedReplyIdsRef = useRef<Set<string>>(new Set());

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

  // リアルタイム受信。event.type で振り分けて messages へ反映する。
  useStompSubscription(`/topic/channels/${params.channelId}`, (frame) => {
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
          if (threadParentId === id) setThreadParentId(null);
        }
        break;
      }
      case "REACTION_ADDED":
      case "REACTION_REMOVED":
        setMessages((prev) => applyReaction(prev, event));
        break;
    }
  });

  // 返信の履歴・WS 受信を同じ id マージで取り込む（昇順なので末尾に足して createdAt 昇順で整える）。
  const mergeReplies = (incoming: Message[]) => {
    setReplies((prev) => {
      const existing = new Set(prev.map((r) => r.id));
      const additions = incoming.filter(
        (r) => !existing.has(r.id) && !deletedReplyIdsRef.current.has(r.id),
      );
      if (additions.length === 0) return prev;
      return [...prev, ...additions].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    });
  };

  // スレッドを開いたら返信履歴を初期ロードする。閉じたら（threadParentId=null）replies を破棄。
  useEffect(() => {
    if (!threadParentId) {
      setReplies([]);
      return;
    }
    deletedReplyIdsRef.current = new Set();
    setReplies([]);
    let cancelled = false;
    void listReplies(threadParentId)
      .then((page) => {
        if (!cancelled) mergeReplies(page.items);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [threadParentId]);

  // スレッドを開いている間だけ /topic/threads/{rootId} を購読し、返信配列へ反映する。
  // 返信は CREATED/EDITED/DELETED/REACTION_* がすべてこのトピックに流れる（backend 仕様）。
  useStompSubscription(threadParentId ? `/topic/threads/${threadParentId}` : null, (frame) => {
    const event = parseWsEvent(frame.body);
    if (!event) return;
    switch (event.type) {
      case "MESSAGE_CREATED": {
        const created = messageCreatedToMessage(event);
        if (created) mergeReplies([created]);
        break;
      }
      case "MESSAGE_EDITED":
        setReplies((prev) => applyEdited(prev, event));
        break;
      case "MESSAGE_DELETED": {
        const id = deletedMessageId(event);
        if (id) {
          deletedReplyIdsRef.current.add(id);
          setReplies((prev) => prev.filter((r) => r.id !== id));
        }
        break;
      }
      case "REACTION_ADDED":
      case "REACTION_REMOVED":
        setReplies((prev) => applyReaction(prev, event));
        break;
    }
  });

  // 親の返信件数バッジ（threadReplyCount / participantIds）は API 非返却なので、
  // 開いているスレッドの loaded replies から best-effort で導出する（WS で増減に追従）。
  useEffect(() => {
    if (!threadParentId) return;
    const count = replies.length;
    const participants = Array.from(new Set(replies.map((r) => r.authorId)));
    setMessages((prev) =>
      prev.map((m) =>
        m.id === threadParentId &&
        (m.threadReplyCount !== count ||
          m.threadParticipantIds.length !== participants.length)
          ? { ...m, threadReplyCount: count, threadParticipantIds: participants }
          : m,
      ),
    );
  }, [replies, threadParentId]);

  const send = (body: string) => {
    publishChannelMessage(params.channelId, body);
  };

  // トップレベルメッセージの編集/削除/リアクションは REST に投げるだけ。state 反映は WS 受信で行う。
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

  // 返信のリアクションは replies 配列で自分判定し、REST に投げるだけ（編集/削除は top-level と同じ関数を再利用）。
  const toggleReplyReaction = (id: string, emoji: string) => {
    if (!meId) return;
    const target = replies.find((r) => r.id === id);
    const mine = target?.reactions.find((r) => r.emoji === emoji)?.userIds.includes(meId) ?? false;
    void (mine ? removeReaction(id, emoji) : addReaction(id, emoji));
  };

  const openThread = (id: string) => setThreadParentId(id);

  const closeThread = () => setThreadParentId(null);

  // 返信投稿は REST POST のみ（WS publish 経路は無い）。確定は thread WS の MESSAGE_CREATED 受信で。
  const replyToThread = (body: string) => {
    if (!threadParentId) return;
    void createReply(threadParentId, body);
  };

  const parent = threadParentId ? messages.find((m) => m.id === threadParentId) : null;

  if (!channel) {
    if (channelLoading) {
      return (
        <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
          読み込み中…
        </div>
      );
    }
    notFound();
  }

  return (
    <>
      <div className="flex-1 flex flex-col min-w-0">
        <ChannelHeader channel={channel} />
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
            onOpenThread={openThread}
            onLoadOlder={() => fetchNextPage()}
            hasMore={hasNextPage}
            loadingOlder={isFetchingNextPage}
          />
        )}
        <MessageInput placeholder={`#${channel.name} へのメッセージ`} onSend={send} />
      </div>
      {parent && (
        <ThreadPanel
          parent={parent}
          replies={replies}
          onClose={closeThread}
          onReply={replyToThread}
          onReactParent={(emoji) => toggleReact(parent.id, emoji)}
          onReactReply={(id, emoji) => toggleReplyReaction(id, emoji)}
          onEditParent={(b) => edit(parent.id, b)}
          onEditReply={(id, b) => edit(id, b)}
          onDeleteParent={() => remove(parent.id)}
          onDeleteReply={(id) => remove(id)}
        />
      )}
    </>
  );
}
