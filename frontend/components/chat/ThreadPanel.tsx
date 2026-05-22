"use client";

import { X } from "lucide-react";
import type { Message } from "@/types";
import { MessageItem } from "./MessageItem";
import { MessageInput } from "./MessageInput";

interface Props {
  parent: Message;
  replies: Message[];
  onClose: () => void;
  onReply: (body: string) => void;
  onReactParent: (emoji: string) => void;
  onReactReply: (id: string, emoji: string) => void;
  onEditParent: (body: string) => void;
  onEditReply: (id: string, body: string) => void;
  onDeleteParent: () => void;
  onDeleteReply: (id: string) => void;
}

export function ThreadPanel({
  parent,
  replies,
  onClose,
  onReply,
  onReactParent,
  onReactReply,
  onEditParent,
  onEditReply,
  onDeleteParent,
  onDeleteReply,
}: Props) {
  return (
    <aside className="w-[400px] border-l border-gray-200 bg-white flex flex-col shrink-0">
      <div className="h-14 px-4 flex items-center justify-between border-b border-gray-200 shrink-0">
        <div>
          <div className="font-bold text-gray-900">スレッド</div>
          <div className="text-xs text-gray-500">{replies.length}件の返信</div>
        </div>
        <button
          onClick={onClose}
          className="text-gray-500 hover:text-gray-900 p-1 rounded hover:bg-gray-100"
          aria-label="閉じる"
        >
          <X size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-thin">
        <MessageItem
          message={parent}
          onReact={onReactParent}
          onEdit={onEditParent}
          onDelete={onDeleteParent}
        />
        {replies.length > 0 && (
          <div className="flex items-center gap-3 px-5 my-1">
            <div className="text-xs text-gray-500">
              {replies.length}件の返信
            </div>
            <div className="flex-1 border-t border-gray-200" />
          </div>
        )}
        {replies.map((r) => (
          <MessageItem
            key={r.id}
            message={r}
            onReact={(emoji) => onReactReply(r.id, emoji)}
            onEdit={(b) => onEditReply(r.id, b)}
            onDelete={() => onDeleteReply(r.id)}
          />
        ))}
      </div>

      <MessageInput placeholder="スレッドに返信..." onSend={onReply} />
    </aside>
  );
}
