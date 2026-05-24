"use client";

import { useState } from "react";
import { Smile, MessageSquare, Edit2, Trash2, Check, X, MoreHorizontal, FileText } from "lucide-react";
import { Avatar } from "@/components/ui/Avatar";
import { MarkdownRenderer } from "./MarkdownRenderer";
import { ReactionBar } from "./ReactionBar";
import type { Message } from "@/types";
import { getUser, currentUserId } from "@/lib/mock/users";
import { formatMessageTime } from "@/lib/utils";

interface Props {
  message: Message;
  onReact: (emoji: string) => void;
  onEdit: (newBody: string) => void;
  onDelete: () => void;
  onOpenThread?: () => void;
  compact?: boolean; // 連続発言の2件目以降はアバター省略
}

const QUICK_EMOJIS = ["👍", "❤️", "😄", "🎉", "🙏", "👀"];

export function MessageItem({
  message,
  onReact,
  onEdit,
  onDelete,
  onOpenThread,
  compact,
}: Props) {
  const author = getUser(message.authorId);
  const isMine = author.id === currentUserId;

  const [hover, setHover] = useState(false);
  const [showEmoji, setShowEmoji] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editValue, setEditValue] = useState(message.body);

  const submitEdit = () => {
    if (editValue.trim().length === 0) return;
    onEdit(editValue.trim());
    setEditing(false);
  };

  return (
    <div
      className="group relative px-5 py-1 hover:bg-gray-50"
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => {
        setHover(false);
        setShowEmoji(false);
      }}
    >
      <div className="flex gap-3">
        {compact ? (
          <div className="w-9 shrink-0 flex justify-end pr-1 text-[11px] text-gray-400 leading-[1.5rem] opacity-0 group-hover:opacity-100">
            {formatMessageTime(message.createdAt)}
          </div>
        ) : (
          <Avatar name={author.displayName} color={author.avatarColor} size="md" />
        )}
        <div className="flex-1 min-w-0">
          {!compact && (
            <div className="flex items-baseline gap-2">
              <span className="font-bold text-gray-900 text-[15px]">{author.displayName}</span>
              <span className="text-xs text-gray-500">{formatMessageTime(message.createdAt)}</span>
              {message.editedAt && <span className="text-xs text-gray-400">（編集済み）</span>}
            </div>
          )}
          {editing ? (
            <div className="mt-1">
              <textarea
                value={editValue}
                onChange={(e) => setEditValue(e.target.value)}
                className="w-full px-3 py-2 text-[15px] border rounded outline-none border-gray-400 resize-none"
                rows={3}
                autoFocus
              />
              <div className="flex gap-2 mt-2">
                <button
                  onClick={submitEdit}
                  className="flex items-center gap-1 px-3 py-1 bg-slack-accent text-white text-xs font-bold rounded"
                >
                  <Check size={12} />
                  保存
                </button>
                <button
                  onClick={() => {
                    setEditing(false);
                    setEditValue(message.body);
                  }}
                  className="flex items-center gap-1 px-3 py-1 bg-white border border-gray-300 text-xs font-bold rounded"
                >
                  <X size={12} />
                  キャンセル
                </button>
              </div>
            </div>
          ) : (
            <MarkdownRenderer body={message.body} />
          )}

          {/* Attachments */}
          {message.attachments.length > 0 && (
            <div className="mt-2 flex flex-col gap-2">
              {message.attachments.map((a) =>
                a.type === "image" ? (
                  <div key={a.id} className="border rounded-lg overflow-hidden max-w-sm">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={a.url} alt={a.name} className="block max-w-full" />
                  </div>
                ) : (
                  <div
                    key={a.id}
                    className="flex items-center gap-2 px-3 py-2 border rounded max-w-xs bg-white"
                  >
                    <FileText size={18} className="text-gray-500" />
                    <div className="text-sm">
                      <div className="font-semibold">{a.name}</div>
                      <div className="text-xs text-gray-500">{(a.sizeBytes / 1024).toFixed(0)} KB</div>
                    </div>
                  </div>
                ),
              )}
            </div>
          )}

          <ReactionBar
            reactions={message.reactions}
            onToggle={(emoji) => onReact(emoji)}
            onAdd={() => setShowEmoji((v) => !v)}
          />

          {/* Thread summary */}
          {message.threadReplyCount > 0 && (
            <button
              onClick={onOpenThread}
              className="mt-1.5 inline-flex items-center gap-2 px-2 py-1 rounded hover:bg-white hover:border hover:shadow-sm border border-transparent transition"
            >
              <div className="flex -space-x-1">
                {message.threadParticipantIds.slice(0, 3).map((id) => {
                  const u = getUser(id);
                  return (
                    <Avatar
                      key={id}
                      name={u.displayName}
                      color={u.avatarColor}
                      size="xs"
                      className="ring-2 ring-white"
                    />
                  );
                })}
              </div>
              <span className="text-xs font-bold text-blue-700">
                {message.threadReplyCount}件の返信
              </span>
              <span className="text-xs text-gray-500">スレッドを表示</span>
            </button>
          )}
        </div>
      </div>

      {/* Hover action bar */}
      {hover && !editing && (
        <div className="absolute -top-3 right-4 bg-white border rounded shadow-md flex items-center px-0.5 py-0.5 z-10">
          <ActionBtn
            label="リアクション"
            onClick={() => setShowEmoji((v) => !v)}
          >
            <Smile size={16} />
          </ActionBtn>
          {onOpenThread && (
            <ActionBtn label="スレッドで返信" onClick={onOpenThread}>
              <MessageSquare size={16} />
            </ActionBtn>
          )}
          {isMine && (
            <>
              <ActionBtn
                label="編集"
                onClick={() => {
                  setEditing(true);
                  setEditValue(message.body);
                }}
              >
                <Edit2 size={16} />
              </ActionBtn>
              <ActionBtn
                label="削除"
                onClick={() => {
                  if (confirm("このメッセージを削除しますか？")) onDelete();
                }}
              >
                <Trash2 size={16} />
              </ActionBtn>
            </>
          )}
          <ActionBtn label="その他">
            <MoreHorizontal size={16} />
          </ActionBtn>
        </div>
      )}

      {/* Quick emoji picker */}
      {showEmoji && (
        <div className="absolute right-4 top-4 bg-white border rounded shadow-lg p-2 z-20 flex gap-1">
          {QUICK_EMOJIS.map((e) => (
            <button
              key={e}
              onClick={() => {
                onReact(e);
                setShowEmoji(false);
              }}
              className="w-8 h-8 hover:bg-gray-100 rounded text-lg"
            >
              {e}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function ActionBtn({
  children,
  label,
  onClick,
}: {
  children: React.ReactNode;
  label: string;
  onClick?: () => void;
}) {
  return (
    <button
      onClick={onClick}
      title={label}
      className="p-1.5 hover:bg-gray-100 rounded text-gray-600 hover:text-gray-900"
    >
      {children}
    </button>
  );
}
