"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { getWorkspaceMembers } from "@/lib/api/workspaces";
import { createDmRoom } from "@/lib/api/dm";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError } from "@/lib/api/problem";
import { cn } from "@/lib/utils";

export function NewDmModal({
  open,
  onClose,
  workspaceId,
}: {
  open: boolean;
  onClose: () => void;
  workspaceId: string;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: members = [], isLoading } = useQuery({
    queryKey: queryKeys.workspaceMembers(workspaceId),
    queryFn: () => getWorkspaceMembers(workspaceId),
    enabled: open && !!workspaceId,
  });

  const meId = user ? String(user.id) : null;

  // 自分を除外し、検索クエリ（表示名 / userId）で絞り込む。
  const candidates = useMemo(() => {
    const q = query.trim().toLowerCase();
    return members
      .filter((m) => m.id !== meId)
      .filter(
        (m) =>
          q === "" ||
          m.displayName.toLowerCase().includes(q) ||
          m.userId.toLowerCase().includes(q),
      );
  }, [members, meId, query]);

  const mutation = useMutation({
    mutationFn: (partnerUserId: string) => createDmRoom(workspaceId, partnerUserId),
    onSuccess: async (room) => {
      // 一覧キャッシュを最新化（既存ルームでも未参加表示だった場合に備える）してから遷移。
      await queryClient.invalidateQueries({ queryKey: queryKeys.dmRooms(workspaceId) });
      reset();
      onClose();
      router.push(`/workspaces/${workspaceId}/dm/${room.id}`);
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "DM の作成に失敗しました");
    },
  });

  const reset = () => {
    setSelectedId(null);
    setQuery("");
    setError(null);
  };

  const close = () => {
    if (mutation.isPending) return;
    reset();
    onClose();
  };

  const submit = () => {
    if (!selectedId || mutation.isPending) return;
    setError(null);
    mutation.mutate(selectedId);
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title="メッセージを作成"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={mutation.isPending}>
            キャンセル
          </Button>
          <Button onClick={submit} disabled={!selectedId || mutation.isPending}>
            {mutation.isPending ? "作成中…" : "メッセージを送る"}
          </Button>
        </>
      }
    >
      <p className="text-sm text-gray-600 mb-3">
        DM を始める相手を選んでください。既に DM がある相手はそのトークへ移動します。
      </p>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="名前で検索"
        className="w-full px-3 py-2 border border-gray-300 rounded outline-none text-sm focus:border-gray-500 mb-3"
      />

      <div className="max-h-72 overflow-y-auto -mx-1 px-1">
        {isLoading ? (
          <p className="text-sm text-gray-500 py-4 text-center">読み込み中…</p>
        ) : candidates.length === 0 ? (
          <p className="text-sm text-gray-500 py-4 text-center">
            DM を始められる相手がいません。
          </p>
        ) : (
          <div className="space-y-0.5">
            {candidates.map((m) => {
              const active = m.id === selectedId;
              return (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => setSelectedId(m.id)}
                  className={cn(
                    "w-full flex items-center gap-3 px-2 py-2 rounded text-left transition",
                    active ? "bg-slack-aubergine/10 ring-1 ring-slack-aubergine" : "hover:bg-gray-100",
                  )}
                >
                  <Avatar name={m.displayName} color={m.avatarColor} size="sm" />
                  <div className="min-w-0">
                    <div className="text-sm font-bold truncate">{m.displayName}</div>
                    <div className="text-xs text-gray-500 truncate">@{m.userId}</div>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
