"use client";

import { useMemo, useState } from "react";
import { notFound } from "next/navigation";
import { getDmRoom } from "@/lib/mock/dms";
import { getDmMessages } from "@/lib/mock/messages";
import { currentUserId, getUser } from "@/lib/mock/users";
import { MessageList } from "@/components/chat/MessageList";
import { MessageInput } from "@/components/chat/MessageInput";
import { Avatar } from "@/components/ui/Avatar";
import type { Message } from "@/types";

export default function DmPage({ params }: { params: { dmId: string } }) {
  const room = getDmRoom(params.dmId);
  if (!room) notFound();
  const partnerId = room.memberIds.find((id) => id !== currentUserId) ?? room.memberIds[0];
  const partner = getUser(partnerId);

  const initial = useMemo(() => getDmMessages(params.dmId), [params.dmId]);
  const [messages, setMessages] = useState<Message[]>(initial);

  const send = (body: string) => {
    const m: Message = {
      id: `m-local-${Date.now()}`,
      dmRoomId: params.dmId,
      authorId: currentUserId,
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
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id !== id) return m;
        const existing = m.reactions.find((r) => r.emoji === emoji);
        if (!existing) {
          return { ...m, reactions: [...m.reactions, { emoji, userIds: [currentUserId] }] };
        }
        const mine = existing.userIds.includes(currentUserId);
        const nextIds = mine
          ? existing.userIds.filter((u) => u !== currentUserId)
          : [...existing.userIds, currentUserId];
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

  const remove = (id: string) =>
    setMessages((prev) => prev.filter((m) => m.id !== id));

  return (
    <div className="flex-1 flex flex-col min-w-0">
      <div className="h-14 px-5 flex items-center gap-3 border-b border-gray-200 bg-white shrink-0">
        <Avatar name={partner.displayName} color={partner.avatarColor} size="sm" />
        <div>
          <div className="font-bold text-gray-900 text-[15px]">{partner.displayName}</div>
          <div className="text-xs text-gray-500 flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            アクティブ
          </div>
        </div>
      </div>
      <MessageList
        messages={messages}
        onReact={toggleReact}
        onEdit={edit}
        onDelete={remove}
      />
      <MessageInput placeholder={`${partner.displayName} へのメッセージ`} onSend={send} />
    </div>
  );
}
