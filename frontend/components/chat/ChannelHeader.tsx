"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Hash, Lock, Star, Info, UserPlus, UserMinus } from "lucide-react";
import type { Channel } from "@/types";
import { Avatar } from "@/components/ui/Avatar";
import { usePresence } from "@/lib/presence/PresenceContext";
import { getChannelMembers } from "@/lib/api/channels";
import { queryKeys } from "@/lib/api/queryKeys";
import { InviteUserModal } from "@/components/modals/InviteUserModal";
import { KickUserModal } from "@/components/modals/KickUserModal";

export function ChannelHeader({ channel }: { channel: Channel }) {
  const [inviteOpen, setInviteOpen] = useState(false);
  const [kickOpen, setKickOpen] = useState(false);
  const { isOnline } = usePresence();

  // メンバー数バッジは実 API（GET /api/channels/{id}/members）の実数を正とする。
  const { data: apiMembers = [] } = useQuery({
    queryKey: queryKeys.channelMembers(channel.id),
    queryFn: () => getChannelMembers(channel.id),
    enabled: !!channel.id,
    staleTime: 30_000,
  });

  // 表示用に { id, displayName, color, online } の共通形へ正規化する。
  const memberViews = apiMembers.map((m) => ({
    id: m.id,
    displayName: m.displayName,
    color: m.avatarColor,
    online: isOnline(m.id),
  }));
  const memberCount = memberViews.length;

  return (
    <>
      <div className="h-14 px-5 flex items-center justify-between border-b border-gray-200 bg-white shrink-0">
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex items-center gap-1.5 font-bold text-gray-900 truncate">
            {channel.type === "private" ? <Lock size={16} /> : <Hash size={16} />}
            <span className="truncate">{channel.name}</span>
            <button className="text-gray-400 hover:text-yellow-500 ml-1" title="スターを付ける">
              <Star size={14} />
            </button>
          </div>
          {channel.topic && (
            <>
              <div className="w-px h-5 bg-gray-300" />
              <div className="text-sm text-gray-600 truncate">{channel.topic}</div>
            </>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            className="flex items-center gap-1 text-xs text-gray-700 px-2 py-1 rounded hover:bg-gray-100"
            title="メンバー一覧"
          >
            <div className="flex -space-x-1.5">
              {memberViews.slice(0, 3).map((m) => (
                <Avatar
                  key={m.id}
                  name={m.displayName}
                  color={m.color}
                  size="xs"
                  className="ring-2 ring-white"
                  online={m.online}
                />
              ))}
            </div>
            <span className="font-semibold">{memberCount}</span>
          </button>
          <button
            onClick={() => setInviteOpen(true)}
            className="flex items-center gap-1 text-xs text-gray-700 px-2 py-1 rounded hover:bg-gray-100"
            title="メンバー招待"
          >
            <UserPlus size={14} />
          </button>
          <button
            onClick={() => setKickOpen(true)}
            className="flex items-center gap-1 text-xs text-gray-700 px-2 py-1 rounded hover:bg-gray-100"
            title="メンバー削除"
          >
            <UserMinus size={14} />
          </button>
          <button
            className="flex items-center gap-1 text-xs text-gray-700 px-2 py-1 rounded hover:bg-gray-100"
            title="詳細"
          >
            <Info size={14} />
          </button>
        </div>
      </div>

      <InviteUserModal open={inviteOpen} onClose={() => setInviteOpen(false)} channel={channel} />
      <KickUserModal open={kickOpen} onClose={() => setKickOpen(false)} channel={channel} />
    </>
  );
}
