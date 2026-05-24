"use client";

import { useMemo, useState } from "react";
import { notFound } from "next/navigation";
import { getChannel } from "@/lib/mock/channels";
import { getChannelMessages, getThreadReplies } from "@/lib/mock/messages";
import { currentUserId } from "@/lib/mock/users";
import { ChannelHeader } from "@/components/chat/ChannelHeader";
import { MessageList } from "@/components/chat/MessageList";
import { MessageInput } from "@/components/chat/MessageInput";
import { ThreadPanel } from "@/components/chat/ThreadPanel";
import type { Message } from "@/types";

export default function ChannelPage({ params }: { params: { channelId: string } }) {
  const channel = getChannel(params.channelId);
  if (!channel) notFound();

  const initial = useMemo(() => getChannelMessages(params.channelId), [params.channelId]);
  const [messages, setMessages] = useState<Message[]>(initial);
  const [threadParentId, setThreadParentId] = useState<string | null>(null);
  const [threadReplies, setThreadReplies] = useState<Record<string, Message[]>>({});

  const send = (body: string) => {
    const m: Message = {
      id: `m-local-${Date.now()}`,
      channelId: params.channelId,
      authorId: currentUserId,
      body,
      createdAt: new Date().toISOString(),
      reactions: [],
      attachments: [],
      mentionIds: extractMentions(body),
      threadReplyCount: 0,
      threadParticipantIds: [],
    };
    setMessages((prev) => [...prev, m]);
  };

  const toggleReact = (id: string, emoji: string) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? toggleReaction(m, emoji) : m)),
    );
    setThreadReplies((prev) => mapValues(prev, (arr) => arr.map((m) => (m.id === id ? toggleReaction(m, emoji) : m))));
  };

  const edit = (id: string, body: string) => {
    setMessages((prev) =>
      prev.map((m) =>
        m.id === id ? { ...m, body, editedAt: new Date().toISOString() } : m,
      ),
    );
    setThreadReplies((prev) =>
      mapValues(prev, (arr) =>
        arr.map((m) => (m.id === id ? { ...m, body, editedAt: new Date().toISOString() } : m)),
      ),
    );
  };

  const remove = (id: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== id));
    if (threadParentId === id) setThreadParentId(null);
  };

  const removeReply = (id: string) => {
    if (!threadParentId) return;
    setThreadReplies((prev) => ({
      ...prev,
      [threadParentId]: (prev[threadParentId] ?? []).filter((m) => m.id !== id),
    }));
    setMessages((prev) =>
      prev.map((m) =>
        m.id === threadParentId
          ? { ...m, threadReplyCount: Math.max(0, m.threadReplyCount - 1) }
          : m,
      ),
    );
  };

  const openThread = (id: string) => {
    setThreadParentId(id);
    if (!threadReplies[id]) {
      setThreadReplies((prev) => ({ ...prev, [id]: getThreadReplies(id) }));
    }
  };

  const closeThread = () => setThreadParentId(null);

  const replyToThread = (body: string) => {
    if (!threadParentId) return;
    const r: Message = {
      id: `r-local-${Date.now()}`,
      channelId: params.channelId,
      parentId: threadParentId,
      authorId: currentUserId,
      body,
      createdAt: new Date().toISOString(),
      reactions: [],
      attachments: [],
      mentionIds: extractMentions(body),
      threadReplyCount: 0,
      threadParticipantIds: [],
    };
    setThreadReplies((prev) => ({
      ...prev,
      [threadParentId]: [...(prev[threadParentId] ?? []), r],
    }));
    setMessages((prev) =>
      prev.map((m) =>
        m.id === threadParentId
          ? {
              ...m,
              threadReplyCount: m.threadReplyCount + 1,
              threadParticipantIds: Array.from(
                new Set([...m.threadParticipantIds, currentUserId]),
              ),
            }
          : m,
      ),
    );
  };

  const parent = threadParentId ? messages.find((m) => m.id === threadParentId) : null;

  return (
    <>
      <div className="flex-1 flex flex-col min-w-0">
        <ChannelHeader channel={channel} />
        <MessageList
          messages={messages}
          onReact={toggleReact}
          onEdit={edit}
          onDelete={remove}
          onOpenThread={openThread}
        />
        <MessageInput placeholder={`#${channel.name} へのメッセージ`} onSend={send} />
      </div>
      {parent && (
        <ThreadPanel
          parent={parent}
          replies={threadReplies[parent.id] ?? []}
          onClose={closeThread}
          onReply={replyToThread}
          onReactParent={(emoji) => toggleReact(parent.id, emoji)}
          onReactReply={(id, emoji) => toggleReact(id, emoji)}
          onEditParent={(b) => edit(parent.id, b)}
          onEditReply={(id, b) => edit(id, b)}
          onDeleteParent={() => remove(parent.id)}
          onDeleteReply={(id) => removeReply(id)}
        />
      )}
    </>
  );
}

function toggleReaction(m: Message, emoji: string): Message {
  const existing = m.reactions.find((r) => r.emoji === emoji);
  if (!existing) {
    return { ...m, reactions: [...m.reactions, { emoji, userIds: [currentUserId] }] };
  }
  const mine = existing.userIds.includes(currentUserId);
  const nextIds = mine
    ? existing.userIds.filter((u) => u !== currentUserId)
    : [...existing.userIds, currentUserId];
  const next = m.reactions
    .map((r) => (r.emoji === emoji ? { ...r, userIds: nextIds } : r))
    .filter((r) => r.userIds.length > 0);
  return { ...m, reactions: next };
}

function extractMentions(body: string): string[] {
  const matches = body.match(/@(\w+)/g) ?? [];
  return matches.map((s) => s.slice(1));
}

function mapValues<T, U>(o: Record<string, T>, f: (v: T) => U): Record<string, U> {
  return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, f(v)]));
}
