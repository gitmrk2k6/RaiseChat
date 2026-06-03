"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Hash, Lock } from "lucide-react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { createChannel } from "@/lib/api/channels";
import { queryKeys } from "@/lib/api/queryKeys";
import { ApiError } from "@/lib/api/problem";

export function CreateChannelModal({
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
  const [name, setName] = useState("");
  const [topic, setTopic] = useState("");
  const [isPrivate, setIsPrivate] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => createChannel(workspaceId, { name, description: topic, isPrivate }),
    onSuccess: async (channel) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels(workspaceId) });
      reset();
      onClose();
      router.push(`/workspaces/${workspaceId}/channels/${channel.id}`);
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "作成に失敗しました");
    },
  });

  const reset = () => {
    setName("");
    setTopic("");
    setIsPrivate(false);
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
      title="チャンネルを作成"
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
        チャンネルはトピックやプロジェクトごとに会話をまとめる場所です。
      </p>
      <label className="block text-sm font-bold text-gray-900 mb-1">名前</label>
      <div className="flex items-center border border-gray-300 rounded focus-within:border-gray-500 px-2 mb-3">
        <span className="text-gray-500">{isPrivate ? <Lock size={14} /> : <Hash size={14} />}</span>
        <input
          value={name}
          onChange={(e) => setName(e.target.value.replace(/\s+/g, "-").toLowerCase())}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.nativeEvent.isComposing) submit();
          }}
          placeholder="例: marketing"
          className="flex-1 px-2 py-2 outline-none text-sm"
          maxLength={80}
          autoFocus
        />
      </div>
      <label className="block text-sm font-bold text-gray-900 mb-1">トピック（任意）</label>
      <input
        value={topic}
        onChange={(e) => setTopic(e.target.value)}
        placeholder="このチャンネルは何に使いますか？"
        className="w-full px-3 py-2 border border-gray-300 rounded outline-none text-sm focus:border-gray-500 mb-3"
        maxLength={255}
      />
      <label className="flex items-start gap-2 mt-2 cursor-pointer">
        <input
          type="checkbox"
          checked={isPrivate}
          onChange={(e) => setIsPrivate(e.target.checked)}
          className="mt-0.5"
        />
        <span>
          <span className="block text-sm font-bold">プライベートにする</span>
          <span className="block text-xs text-gray-600">招待されたメンバーだけが参加できます</span>
        </span>
      </label>

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
