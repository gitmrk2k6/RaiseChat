"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ChevronDown, ChevronRight, Hash, Lock, Plus, Edit3 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { listChannels } from "@/lib/api/channels";
import { listDmRooms } from "@/lib/api/dm";
import { getWorkspace } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { Avatar } from "@/components/ui/Avatar";
import { cn } from "@/lib/utils";
import { CreateChannelModal } from "@/components/modals/CreateChannelModal";

export function Sidebar() {
  const params = useParams<{ workspaceId: string; channelId?: string; dmId?: string }>();
  const workspaceId = params.workspaceId;
  const { user } = useAuth();

  const { data: workspace } = useQuery({
    queryKey: queryKeys.workspace(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
    enabled: !!workspaceId,
  });
  const { data: channels = [] } = useQuery({
    queryKey: queryKeys.channels(workspaceId),
    queryFn: () => listChannels(workspaceId),
    enabled: !!workspaceId,
  });

  const { data: dms = [] } = useQuery({
    queryKey: queryKeys.dmRooms(workspaceId),
    queryFn: () => listDmRooms(workspaceId),
    enabled: !!workspaceId,
  });
  const meId = user ? String(user.id) : null;

  const [channelsOpen, setChannelsOpen] = useState(true);
  const [dmsOpen, setDmsOpen] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <>
      <aside className="w-64 bg-slack-aubergine text-slack-textOnPurple flex flex-col shrink-0">
        <div className="px-4 py-3 border-b border-slack-divider flex items-center justify-between">
          <div>
            <div className="text-white font-bold text-base leading-tight">
              {workspace?.name ?? "…"}
            </div>
            <div className="text-xs text-slack-textOnPurple/80 flex items-center gap-1.5 mt-0.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
              {user?.displayName ?? ""}
            </div>
          </div>
          <button
            className="text-white p-1.5 rounded hover:bg-white/10"
            title="メッセージ作成（モック）"
          >
            <Edit3 size={16} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto scrollbar-thin py-2">
          {/* Channels */}
          <SidebarSection
            label="チャンネル"
            open={channelsOpen}
            onToggle={() => setChannelsOpen((v) => !v)}
            onAdd={() => setCreateOpen(true)}
          >
            {channels.map((c) => {
              const active = c.id === params.channelId;
              return (
                <Link
                  key={c.id}
                  href={`/workspaces/${workspaceId}/channels/${c.id}`}
                  className={cn(
                    "flex items-center justify-between gap-2 px-4 py-1 text-sm transition",
                    active
                      ? "bg-slack-highlight text-white font-bold"
                      : "text-slack-textOnPurple hover:bg-white/5",
                    c.unreadCount > 0 && !active && "text-white font-semibold",
                  )}
                >
                  <span className="flex items-center gap-2 truncate">
                    {c.type === "private" ? <Lock size={14} /> : <Hash size={14} />}
                    <span className="truncate">{c.name}</span>
                  </span>
                  {c.hasMention ? (
                    <span className="bg-slack-unread text-white text-[10px] font-bold rounded-full px-1.5 min-w-[18px] h-[18px] flex items-center justify-center">
                      {c.unreadCount}
                    </span>
                  ) : c.unreadCount > 0 && !active ? (
                    <span className="bg-white/20 text-white text-[10px] font-bold rounded-full px-1.5 min-w-[18px] h-[18px] flex items-center justify-center">
                      {c.unreadCount}
                    </span>
                  ) : null}
                </Link>
              );
            })}
          </SidebarSection>

          {/* DMs */}
          <SidebarSection
            label="ダイレクトメッセージ"
            open={dmsOpen}
            onToggle={() => setDmsOpen((v) => !v)}
          >
            {dms.map((d) => {
              const partner =
                d.members?.find((m) => m.id !== meId) ?? d.members?.[0];
              const active = d.id === params.dmId;
              return (
                <Link
                  key={d.id}
                  href={`/workspaces/${workspaceId}/dm/${d.id}`}
                  className={cn(
                    "flex items-center justify-between gap-2 px-4 py-1 text-sm transition",
                    active
                      ? "bg-slack-highlight text-white font-bold"
                      : "text-slack-textOnPurple hover:bg-white/5",
                    d.unreadCount > 0 && !active && "text-white font-semibold",
                  )}
                >
                  <span className="flex items-center gap-2 truncate">
                    <Avatar
                      name={partner?.displayName ?? "?"}
                      color={partner?.avatarColor ?? "#6B7280"}
                      size="xs"
                    />
                    <span className="truncate">{partner?.displayName ?? "(不明)"}</span>
                  </span>
                  {d.unreadCount > 0 && !active && (
                    <span className="bg-slack-unread text-white text-[10px] font-bold rounded-full px-1.5 min-w-[18px] h-[18px] flex items-center justify-center">
                      {d.unreadCount}
                    </span>
                  )}
                </Link>
              );
            })}
          </SidebarSection>
        </div>
      </aside>

      <CreateChannelModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </>
  );
}

function SidebarSection({
  label,
  open,
  onToggle,
  onAdd,
  children,
}: {
  label: string;
  open: boolean;
  onToggle: () => void;
  onAdd?: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-3">
      <div className="flex items-center justify-between px-2 group">
        <button
          onClick={onToggle}
          className="flex items-center gap-1 text-xs uppercase tracking-wider text-slack-textOnPurple/80 hover:text-white py-1 px-2"
        >
          {open ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
          {label}
        </button>
        {onAdd && (
          <button
            onClick={onAdd}
            className="text-slack-textOnPurple/60 hover:text-white p-1 opacity-0 group-hover:opacity-100 transition"
            title="追加"
          >
            <Plus size={14} />
          </button>
        )}
      </div>
      {open && <div className="mt-0.5">{children}</div>}
    </div>
  );
}
