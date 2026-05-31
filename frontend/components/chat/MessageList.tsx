"use client";

import { Fragment, useLayoutEffect, useRef } from "react";
import type { Message } from "@/types";
import { MessageItem } from "./MessageItem";
import { dayKey, formatDayLabel } from "@/lib/utils";

interface Props {
  messages: Message[];
  onReact: (id: string, emoji: string) => void;
  onEdit: (id: string, newBody: string) => void;
  onDelete: (id: string) => void;
  onOpenThread?: (id: string) => void;
  /** 上方向スクロールで古い履歴を追加読込するコールバック。 */
  onLoadOlder?: () => void;
  /** さらに古い履歴があるか。 */
  hasMore?: boolean;
  /** 古い履歴の読込中フラグ。 */
  loadingOlder?: boolean;
}

export function MessageList({
  messages,
  onReact,
  onEdit,
  onDelete,
  onOpenThread,
  onLoadOlder,
  hasMore,
  loadingOlder,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const didInitRef = useRef(false);
  // 古い履歴を prepend する直前の scrollHeight。prepend 後に位置を復元するためのアンカー。
  const anchorHeightRef = useRef<number | null>(null);
  const lastIdRef = useRef<string | null>(null);

  // スクロール位置の制御:
  //  - 初回ロード → 最下部へ
  //  - 古い履歴 prepend → 増えた分だけ scrollTop を補正し、見えている位置を維持
  //  - 新着（最下部に追加）→ 最下部へ追従
  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    if (anchorHeightRef.current != null) {
      el.scrollTop = el.scrollHeight - anchorHeightRef.current;
      anchorHeightRef.current = null;
      return;
    }

    const lastId = messages[messages.length - 1]?.id ?? null;

    if (!didInitRef.current && messages.length > 0) {
      el.scrollTop = el.scrollHeight;
      didInitRef.current = true;
      lastIdRef.current = lastId;
      return;
    }

    if (lastId && lastId !== lastIdRef.current) {
      el.scrollTop = el.scrollHeight;
      lastIdRef.current = lastId;
    }
  }, [messages]);

  const handleScroll = () => {
    const el = containerRef.current;
    if (!el || !onLoadOlder || !hasMore || loadingOlder) return;
    if (el.scrollTop < 80) {
      anchorHeightRef.current = el.scrollHeight;
      onLoadOlder();
    }
  };

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
    <div
      ref={containerRef}
      onScroll={handleScroll}
      className="flex-1 overflow-y-auto scrollbar-thin py-3"
    >
      {loadingOlder && (
        <div className="py-2 text-center text-xs text-gray-400">読み込み中…</div>
      )}
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
