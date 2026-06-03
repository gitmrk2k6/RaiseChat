"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { getChannelMembers, removeChannelMember } from "@/lib/api/channels";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError } from "@/lib/api/problem";
import type { Channel } from "@/types";

export function KickUserModal({
  open,
  onClose,
  channel,
}: {
  open: boolean;
  onClose: () => void;
  channel: Channel;
}) {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const { data: members = [], isLoading } = useQuery({
    queryKey: queryKeys.channelMembers(channel.id),
    queryFn: () => getChannelMembers(channel.id),
    enabled: open && !!channel.id,
  });

  const meId = user ? String(user.id) : null;
  // 自分自身はキックできない（退出を使う）ので候補から除外する。
  const others = members.filter((m) => m.id !== meId);

  const mutation = useMutation({
    mutationFn: (userId: string) => removeChannelMember(channel.id, userId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.channelMembers(channel.id) });
      setError(null);
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "削除に失敗しました");
    },
    onSettled: () => setPendingId(null),
  });

  const kick = (userId: string, name: string) => {
    if (mutation.isPending) return;
    if (!confirm(`${name} を #${channel.name} から削除しますか？`)) return;
    setError(null);
    setPendingId(userId);
    mutation.mutate(userId);
  };

  return (
    <Modal open={open} onClose={onClose} title={`#${channel.name} のメンバー管理`}>
      <p className="text-sm text-gray-600 mb-3">
        メンバーを削除（キック）できます。オーナーまたは作成者のみ可能です。
      </p>
      {isLoading ? (
        <p className="text-sm text-gray-500 py-4 text-center">読み込み中…</p>
      ) : others.length === 0 ? (
        <p className="text-sm text-gray-600">削除できるメンバーはいません。</p>
      ) : (
        <div className="space-y-1 max-h-72 overflow-y-auto">
          {others.map((m) => (
            <div
              key={m.id}
              className="flex items-center gap-3 px-2 py-2 rounded hover:bg-gray-100"
            >
              <Avatar name={m.displayName} color={m.avatarColor} size="sm" />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold truncate">{m.displayName}</div>
                <div className="text-xs text-gray-500 truncate">@{m.userId}</div>
              </div>
              <Button
                variant="danger"
                onClick={() => kick(m.id, m.displayName)}
                disabled={mutation.isPending}
              >
                {pendingId === m.id ? "削除中…" : "削除"}
              </Button>
            </div>
          ))}
        </div>
      )}

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
