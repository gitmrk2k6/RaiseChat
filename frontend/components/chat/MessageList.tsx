"use client";

import { Fragment } from "react";
import type { Message } from "@/types";
import { MessageItem } from "./MessageItem";
import { dayKey, formatDayLabel } from "@/lib/utils";

interface Props {
  messages: Message[];
  onReact: (id: string, emoji: string) => void;
  onEdit: (id: string, newBody: string) => void;
  onDelete: (id: string) => void;
  onOpenThread?: (id: string) => void;
}

export function MessageList({ messages, onReact, onEdit, onDelete, onOpenThread }: Props) {
  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        メッセージはまだありません
      </div>
    );
  }
  let lastDay = "";
  let lastAuthor = "";
  let lastTime = 0;
  return (
    <div className="flex-1 overflow-y-auto scrollbar-thin py-3">
      {messages.map((m) => {
        const dk = dayKey(m.createdAt);
        const showDay = dk !== lastDay;
        const t = new Date(m.createdAt).getTime();
        const compact = !showDay && m.authorId === lastAuthor && t - lastTime < 5 * 60 * 1000;
        lastDay = dk;
        lastAuthor = m.authorId;
        lastTime = t;
        return (
          <Fragment key={m.id}>
            {showDay && (
              <div className="flex items-center gap-3 my-3 px-5">
                <div className="flex-1 border-t border-gray-200" />
                <div className="text-xs font-bold text-gray-700 bg-white border border-gray-200 rounded-full px-3 py-1">
                  {formatDayLabel(m.createdAt)}
                </div>
                <div className="flex-1 border-t border-gray-200" />
              </div>
            )}
            <MessageItem
              message={m}
              onReact={(emoji) => onReact(m.id, emoji)}
              onEdit={(b) => onEdit(m.id, b)}
              onDelete={() => onDelete(m.id)}
              onOpenThread={onOpenThread ? () => onOpenThread(m.id) : undefined}
              compact={compact}
            />
          </Fragment>
        );
      })}
    </div>
  );
}
