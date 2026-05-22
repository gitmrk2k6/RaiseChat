"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Plus } from "lucide-react";
import { workspaces } from "@/lib/mock/workspaces";
import { cn } from "@/lib/utils";

export function WorkspaceRail() {
  const params = useParams<{ workspaceId?: string }>();
  const activeId = params.workspaceId ?? "ws-1";

  return (
    <nav className="w-16 bg-slack-rail flex flex-col items-center py-3 gap-3 shrink-0">
      {workspaces.map((ws) => {
        const active = ws.id === activeId;
        return (
          <Link
            key={ws.id}
            href={`/workspaces/${ws.id}`}
            className={cn(
              "w-10 h-10 rounded-lg flex items-center justify-center text-white font-bold text-lg transition relative",
              active && "ring-2 ring-white",
            )}
            style={{ backgroundColor: ws.color }}
            title={ws.name}
          >
            {ws.initial}
          </Link>
        );
      })}
      <button
        className="w-10 h-10 rounded-lg flex items-center justify-center text-white/70 hover:text-white bg-white/10 hover:bg-white/20"
        title="ワークスペース追加（モック）"
      >
        <Plus size={20} />
      </button>
    </nav>
  );
}
