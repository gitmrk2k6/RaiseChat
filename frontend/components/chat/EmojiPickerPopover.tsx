"use client";

import { useEffect, useRef } from "react";
import dynamic from "next/dynamic";
// 型のみ取り込む（import type は実行時に消えるので、重いライブラリ本体はバンドルされない）。
import type { EmojiClickData, EmojiStyle, Theme } from "emoji-picker-react";

// 絵文字データを含む重いコンポーネントは、ポップオーバーを開いたときだけ遅延ロードする。
// ssr: false（クライアント専用 = window 依存のため）。
const EmojiPicker = dynamic(() => import("emoji-picker-react"), {
  ssr: false,
  loading: () => (
    <div className="flex items-center justify-center w-[320px] h-[360px] text-sm text-gray-400">
      読み込み中…
    </div>
  ),
});

// 文字列バックの enum なので、型のみ import + 文字列リテラルを cast して runtime 値を渡す。
const NATIVE = "native" as EmojiStyle;
const LIGHT = "light" as Theme;

export function EmojiPickerPopover({
  onPick,
  onClose,
}: {
  /** 絵文字を選択したとき。glyph（"😀" など）が渡る。複数選択できるよう閉じない。 */
  onPick: (emoji: string) => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  // 外側クリック / Escape で閉じる。
  useEffect(() => {
    const onMouseDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  return (
    <div
      ref={ref}
      className="absolute bottom-full right-5 mb-2 z-20 shadow-lg rounded-lg overflow-hidden"
    >
      <EmojiPicker
        onEmojiClick={(data: EmojiClickData) => onPick(data.emoji)}
        emojiStyle={NATIVE}
        theme={LIGHT}
        lazyLoadEmojis
        width={320}
        height={360}
        previewConfig={{ showPreview: false }}
        searchPlaceHolder="絵文字を検索"
        autoFocusSearch={false}
      />
    </div>
  );
}
