"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { getWorkspaceMembers } from "@/lib/api/workspaces";
import { addChannelMembers } from "@/lib/api/channels";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError } from "@/lib/api/problem";
import type { Channel } from "@/types";

export function InviteUserModal({
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
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  const { data: members = [], isLoading } = useQuery({
    queryKey: queryKeys.workspaceMembers(channel.workspaceId),
    queryFn: () => getWorkspaceMembers(channel.workspaceId),
    enabled: open && !!channel.workspaceId,
  });

  const meId = user ? String(user.id) : null;
  // 自分は作成者として既にメンバーなので候補から除外する。
  // 既にチャンネルに居る他メンバーはここでは判別できないが、追加 API が冪等なので再選択しても無害。
  const candidates = members.filter((m) => m.id !== meId);

  const mutation = useMutation({
    mutationFn: () => addChannelMembers(channel.id, selected),
    onSuccess: async () => {
      // 追加された側のサイドバーは次回読み込みで反映される。操作者側の一覧も一応最新化しておく。
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels(channel.workspaceId) });
      setSelected([]);
      setError(null);
      onClose();
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "追加に失敗しました");
    },
  });

  const toggle = (id: string) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const close = () => {
    if (mutation.isPending) return;
    setSelected([]);
    setError(null);
    onClose();
  };

  const submit = () => {
    if (selected.length === 0 || mutation.isPending) return;
    setError(null);
    mutation.mutate();
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title={`#${channel.name} にメンバーを追加`}
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={mutation.isPending}>
            キャンセル
          </Button>
          <Button onClick={submit} disabled={selected.length === 0 || mutation.isPending}>
            {mutation.isPending ? "追加中…" : "追加"}
          </Button>
        </>
      }
    >
      {isLoading ? (
        <p className="text-sm text-gray-500 py-4 text-center">読み込み中…</p>
      ) : candidates.length === 0 ? (
        <p className="text-sm text-gray-600">追加できるメンバーはいません。</p>
      ) : (
        <div className="space-y-1 max-h-72 overflow-y-auto">
          {candidates.map((m) => (
            <label
              key={m.id}
              className="flex items-center gap-3 px-2 py-2 rounded hover:bg-gray-100 cursor-pointer"
            >
              <input
                type="checkbox"
                checked={selected.includes(m.id)}
                onChange={() => toggle(m.id)}
              />
              <Avatar name={m.displayName} color={m.avatarColor} size="sm" />
              <div className="min-w-0">
                <div className="text-sm font-bold truncate">{m.displayName}</div>
                <div className="text-xs text-gray-500 truncate">@{m.userId}</div>
              </div>
            </label>
          ))}
        </div>
      )}

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
