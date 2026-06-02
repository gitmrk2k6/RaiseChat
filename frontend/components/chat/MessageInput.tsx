"use client";

import { useState, useRef, KeyboardEvent } from "react";
import { Bold, Italic, Code, List, Paperclip, AtSign, Smile, Send } from "lucide-react";
import { users, currentUserId } from "@/lib/mock/users";
import { Avatar } from "@/components/ui/Avatar";

interface Props {
  placeholder: string;
  onSend: (body: string) => void;
  /** 入力中（typing）通知。打鍵のたびに呼ぶ（throttle は呼び出し側の責務）。 */
  onTyping?: () => void;
}

export function MessageInput({ placeholder, onSend, onTyping }: Props) {
  const [value, setValue] = useState("");
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleChange = (v: string) => {
    setValue(v);
    // 入力が増えたときだけ typing を通知する（全選択削除や貼り付け直後の空化では送らない）。
    if (v.length > 0) onTyping?.();
    // メンションサジェスト判定
    const match = v.match(/(^|\s)@(\w*)$/);
    setMentionQuery(match ? match[2] : null);
  };

  const insertAtCursor = (insert: string, wrapEnd = "") => {
    const ta = textareaRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const next = value.slice(0, start) + insert + value.slice(start, end) + wrapEnd + value.slice(end);
    setValue(next);
    setTimeout(() => ta.focus(), 0);
  };

  const pickMention = (username: string) => {
    const next = value.replace(/@(\w*)$/, `@${username} `);
    setValue(next);
    setMentionQuery(null);
    textareaRef.current?.focus();
  };

  const submit = () => {
    if (value.trim().length === 0) return;
    onSend(value.trim());
    setValue("");
    setMentionQuery(null);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const suggestions =
    mentionQuery !== null
      ? users
          .filter(
            (u) =>
              u.id !== currentUserId &&
              (mentionQuery === "" ||
                u.username.toLowerCase().startsWith(mentionQuery.toLowerCase())),
          )
          .slice(0, 5)
      : [];

  return (
    <div className="px-5 pb-5 relative">
      {suggestions.length > 0 && (
        <div className="absolute bottom-full left-5 right-5 mb-1 bg-white border rounded-md shadow-lg overflow-hidden z-10">
          <div className="px-3 py-1.5 text-xs font-bold text-gray-500 border-b bg-gray-50">
            メンバー候補
          </div>
          {suggestions.map((u) => (
            <button
              key={u.id}
              onClick={() => pickMention(u.username)}
              className="w-full flex items-center gap-2 px-3 py-1.5 hover:bg-gray-100 text-sm text-left"
            >
              <Avatar name={u.displayName} color={u.avatarColor} size="xs" />
              <span className="font-semibold">@{u.username}</span>
              <span className="text-gray-500 text-xs">{u.displayName}</span>
            </button>
          ))}
        </div>
      )}

      <div className="border border-gray-300 rounded-lg focus-within:border-gray-500 transition bg-white">
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => handleChange(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          rows={1}
          className="w-full px-3 py-2.5 text-[15px] outline-none resize-none min-h-[44px] max-h-40"
          style={{ fieldSizing: "content" } as React.CSSProperties}
        />
        <div className="flex items-center justify-between border-t border-gray-200 px-2 py-1.5">
          <div className="flex items-center gap-0.5 text-gray-500">
            <ToolbarBtn label="太字" onClick={() => insertAtCursor("**", "**")}>
              <Bold size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="斜体" onClick={() => insertAtCursor("*", "*")}>
              <Italic size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="コード" onClick={() => insertAtCursor("`", "`")}>
              <Code size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="箇条書き" onClick={() => insertAtCursor("- ")}>
              <List size={14} />
            </ToolbarBtn>
            <span className="w-px h-4 bg-gray-300 mx-1" />
            <ToolbarBtn label="ファイル添付（モック）" onClick={() => alert("ファイル添付（モック）")}>
              <Paperclip size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="メンション" onClick={() => insertAtCursor("@")}>
              <AtSign size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="絵文字（モック）" onClick={() => insertAtCursor("😀 ")}>
              <Smile size={14} />
            </ToolbarBtn>
          </div>
          <button
            onClick={submit}
            disabled={value.trim().length === 0}
            className="flex items-center gap-1 bg-slack-accent text-white text-xs font-bold px-2.5 py-1 rounded disabled:bg-gray-300"
          >
            <Send size={12} />
            送信
          </button>
        </div>
      </div>
      <div className="text-xs text-gray-400 mt-1 px-1">
        Markdown対応 / Enter: 送信 / Shift+Enter: 改行
      </div>
    </div>
  );
}

function ToolbarBtn({
  children,
  label,
  onClick,
}: {
  children: React.ReactNode;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      className="p-1.5 rounded hover:bg-gray-100 hover:text-gray-700"
    >
      {children}
    </button>
  );
}
