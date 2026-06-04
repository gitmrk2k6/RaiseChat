"use client";

import { useState, useRef, KeyboardEvent, ChangeEvent } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Bold, Italic, Code, List, Paperclip, AtSign, Smile, Send, X, FileText } from "lucide-react";
import { getWorkspaceMembers } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { Avatar } from "@/components/ui/Avatar";
import { EmojiPickerPopover } from "@/components/chat/EmojiPickerPopover";

// バックエンド（AttachmentService）の許可 MIME / サイズと一致させる。事前にクライアントでも弾く。
const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4"];
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_FILES = 10;

interface Props {
  placeholder: string;
  onSend: (body: string) => void;
  /**
   * 添付つき送信（F-10）。指定された場合のみクリップ（ファイル添付）が有効になる。
   * 確定は WS の MESSAGE_CREATED 受信で行うため、ここでは送信完了を待つ Promise を返すだけでよい。
   */
  onSendFiles?: (body: string, files: File[]) => Promise<void>;
  /** 入力中（typing）通知。打鍵のたびに呼ぶ（throttle は呼び出し側の責務）。 */
  onTyping?: () => void;
}

export function MessageInput({ placeholder, onSend, onSendFiles, onTyping }: Props) {
  // @メンション候補は実ワークスペースメンバー（GET /api/workspaces/{id}）から。
  // ハンドルは backend の MentionService が抽出する userId に合わせる。
  const params = useParams<{ workspaceId: string }>();
  const workspaceId = params.workspaceId;
  const { user } = useAuth();
  const { data: members = [] } = useQuery({
    queryKey: queryKeys.workspaceMembers(workspaceId),
    queryFn: () => getWorkspaceMembers(workspaceId),
    enabled: !!workspaceId,
    staleTime: 30_000,
  });

  const [value, setValue] = useState("");
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const [showEmoji, setShowEmoji] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  const pickMention = (userId: string) => {
    const next = value.replace(/@(\w*)$/, `@${userId} `);
    setValue(next);
    setMentionQuery(null);
    textareaRef.current?.focus();
  };

  const onFilesSelected = (e: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = ""; // 同じファイルを連続で選び直せるよう入力をリセットする。
    const errors: string[] = [];
    const valid: File[] = [];
    for (const f of picked) {
      if (!ALLOWED_TYPES.includes(f.type)) {
        errors.push(`${f.name}: 未対応の形式です`);
      } else if (f.size > MAX_FILE_BYTES) {
        errors.push(`${f.name}: 10MB を超えています`);
      } else {
        valid.push(f);
      }
    }
    setFiles((prev) => {
      const merged = [...prev, ...valid];
      if (merged.length > MAX_FILES) {
        errors.push(`添付できるファイルは最大 ${MAX_FILES} 件です`);
        return merged.slice(0, MAX_FILES);
      }
      return merged;
    });
    setFileError(errors.length > 0 ? errors.join(" / ") : null);
  };

  const removeFile = (idx: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== idx));
    setFileError(null);
  };

  const submit = () => {
    const body = value.trim();
    // 本文か添付のどちらかがあれば送信可（file-only＝本文なし添付も許可）。
    if ((body.length === 0 && files.length === 0) || uploading) return;

    if (files.length > 0 && onSendFiles) {
      setUploading(true);
      void Promise.resolve(onSendFiles(body, files))
        .then(() => {
          setValue("");
          setFiles([]);
          setMentionQuery(null);
          setFileError(null);
        })
        .catch((err) => {
          setFileError(err instanceof Error ? err.message : "送信に失敗しました");
        })
        .finally(() => setUploading(false));
      return;
    }

    onSend(body);
    setValue("");
    setMentionQuery(null);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const meUserId = user?.userId ?? null;
  const suggestions =
    mentionQuery !== null
      ? members
          .filter(
            (m) =>
              m.userId !== meUserId &&
              (mentionQuery === "" ||
                m.userId.toLowerCase().startsWith(mentionQuery.toLowerCase())),
          )
          .slice(0, 5)
      : [];

  return (
    <div className="px-5 pb-5 relative">
      {showEmoji && (
        <EmojiPickerPopover
          onPick={(emoji) => insertAtCursor(emoji)}
          onClose={() => setShowEmoji(false)}
        />
      )}

      {suggestions.length > 0 && (
        <div className="absolute bottom-full left-5 right-5 mb-1 bg-white border rounded-md shadow-lg overflow-hidden z-10">
          <div className="px-3 py-1.5 text-xs font-bold text-gray-500 border-b bg-gray-50">
            メンバー候補
          </div>
          {suggestions.map((m) => (
            <button
              key={m.id}
              onClick={() => pickMention(m.userId)}
              className="w-full flex items-center gap-2 px-3 py-1.5 hover:bg-gray-100 text-sm text-left"
            >
              <Avatar name={m.displayName} color={m.avatarColor} size="xs" />
              <span className="font-semibold">@{m.userId}</span>
              <span className="text-gray-500 text-xs">{m.displayName}</span>
            </button>
          ))}
        </div>
      )}

      <div className="border border-gray-300 rounded-lg focus-within:border-gray-500 transition bg-white">
        {files.length > 0 && (
          <div className="flex flex-wrap gap-2 px-3 pt-3">
            {files.map((f, i) => {
              const isImage = f.type.startsWith("image/");
              const preview = isImage ? URL.createObjectURL(f) : null;
              return (
                <div
                  key={`${f.name}-${i}`}
                  className="flex items-center gap-2 border border-gray-200 rounded-md pl-1.5 pr-1 py-1 bg-gray-50 max-w-[220px]"
                >
                  {preview ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={preview} alt={f.name} className="w-8 h-8 rounded object-cover" />
                  ) : (
                    <FileText size={18} className="text-gray-500 shrink-0" />
                  )}
                  <span className="text-xs text-gray-700 truncate">{f.name}</span>
                  <button
                    type="button"
                    onClick={() => removeFile(i)}
                    title="削除"
                    className="text-gray-400 hover:text-gray-700 p-0.5 rounded hover:bg-gray-200 shrink-0"
                  >
                    <X size={12} />
                  </button>
                </div>
              );
            })}
          </div>
        )}
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
            {onSendFiles && (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept={ALLOWED_TYPES.join(",")}
                  onChange={onFilesSelected}
                  className="hidden"
                />
                <ToolbarBtn label="ファイル添付" onClick={() => fileInputRef.current?.click()}>
                  <Paperclip size={14} />
                </ToolbarBtn>
              </>
            )}
            <ToolbarBtn label="メンション" onClick={() => insertAtCursor("@")}>
              <AtSign size={14} />
            </ToolbarBtn>
            <ToolbarBtn label="絵文字" onClick={() => setShowEmoji((v) => !v)}>
              <Smile size={14} />
            </ToolbarBtn>
          </div>
          <button
            onClick={submit}
            disabled={(value.trim().length === 0 && files.length === 0) || uploading}
            className="flex items-center gap-1 bg-slack-accent text-white text-xs font-bold px-2.5 py-1 rounded disabled:bg-gray-300"
          >
            <Send size={12} />
            {uploading ? "送信中…" : "送信"}
          </button>
        </div>
      </div>
      {fileError && <div className="text-xs text-red-600 mt-1 px-1">{fileError}</div>}
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
