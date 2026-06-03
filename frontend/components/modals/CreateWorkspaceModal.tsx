"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { createWorkspace } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { ApiError } from "@/lib/api/problem";

export function CreateWorkspaceModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => createWorkspace({ name, description }),
    onSuccess: async (ws) => {
      // 一覧キャッシュを最新化してから新ワークスペースへ遷移する。
      await queryClient.invalidateQueries({ queryKey: queryKeys.workspaces });
      reset();
      onClose();
      router.push(`/workspaces/${ws.id}`);
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "作成に失敗しました");
    },
  });

  const reset = () => {
    setName("");
    setDescription("");
    setError(null);
  };

  const close = () => {
    if (mutation.isPending) return;
    reset();
    onClose();
  };

  const submit = () => {
    if (name.trim().length === 0 || mutation.isPending) return;
    setError(null);
    mutation.mutate();
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title="ワークスペースを作成"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={mutation.isPending}>
            キャンセル
          </Button>
          <Button onClick={submit} disabled={name.trim().length === 0 || mutation.isPending}>
            {mutation.isPending ? "作成中…" : "作成"}
          </Button>
        </>
      }
    >
      <p className="text-sm text-gray-600 mb-4">
        ワークスペースはチームやプロジェクトの共有スペースです。あなたがオーナーになります。
      </p>
      <label className="block text-sm font-bold text-gray-900 mb-1">名前</label>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.nativeEvent.isComposing) submit();
        }}
        placeholder="例: RaiseTech AI"
        className="w-full px-3 py-2 border border-gray-300 rounded outline-none text-sm focus:border-gray-500 mb-1"
        maxLength={64}
        autoFocus
      />
      <p className="text-xs text-gray-400 mb-3">1〜64 文字</p>

      <label className="block text-sm font-bold text-gray-900 mb-1">説明（任意）</label>
      <input
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="このワークスペースは何に使いますか？"
        className="w-full px-3 py-2 border border-gray-300 rounded outline-none text-sm focus:border-gray-500 mb-1"
        maxLength={255}
      />
      <p className="text-xs text-gray-400">0〜255 文字</p>

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
