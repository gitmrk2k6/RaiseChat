"use client";

// 「○○ が入力中…」の小さな表示。入力欄の直上に置く。
//  - typers が空のときは高さを確保したまま何も描かない（出入りで入力欄が跳ねないように）
//  - 3 人以上は「○○、△△ ほかが入力中…」に丸める

interface Props {
  typers: { userId: string; displayName: string }[];
}

function buildText(names: string[]): string {
  if (names.length === 1) return `${names[0]} が入力中…`;
  if (names.length === 2) return `${names[0]}、${names[1]} が入力中…`;
  return `${names[0]}、${names[1]} ほかが入力中…`;
}

export function TypingIndicator({ typers }: Props) {
  const names = typers.map((t) => t.displayName);
  return (
    <div className="h-4 px-6 text-xs text-gray-500 italic truncate" aria-live="polite">
      {names.length > 0 ? buildText(names) : ""}
    </div>
  );
}
