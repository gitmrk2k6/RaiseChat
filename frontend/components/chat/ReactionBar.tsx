"use client";

import { SmilePlus } from "lucide-react";
import { Reaction } from "@/types";
import { currentUserId } from "@/lib/mock/users";
import { cn } from "@/lib/utils";

interface Props {
  reactions: Reaction[];
  onToggle: (emoji: string) => void;
  onAdd: () => void;
}

export function ReactionBar({ reactions, onToggle, onAdd }: Props) {
  if (reactions.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1 mt-1.5">
      {reactions.map((r) => {
        const mine = r.userIds.includes(currentUserId);
        return (
          <button
            key={r.emoji}
            onClick={() => onToggle(r.emoji)}
            className={cn(
              "flex items-center gap-1 px-2 py-0.5 rounded-full border text-xs transition",
              mine
                ? "bg-blue-50 border-blue-400 text-blue-700"
                : "bg-gray-100 border-gray-200 text-gray-700 hover:bg-gray-200",
            )}
            title={`${r.userIds.length}人がリアクション`}
          >
            <span className="text-sm leading-none">{r.emoji}</span>
            <span className="font-semibold">{r.userIds.length}</span>
          </button>
        );
      })}
      <button
        onClick={onAdd}
        className="flex items-center px-2 py-0.5 rounded-full border border-gray-200 text-gray-500 hover:bg-gray-100"
        title="リアクションを追加"
      >
        <SmilePlus size={14} />
      </button>
    </div>
  );
}
