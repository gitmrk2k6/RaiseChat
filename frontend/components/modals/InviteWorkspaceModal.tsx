"use client";

import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Check, Copy, Link as LinkIcon } from "lucide-react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { createWorkspaceInvite } from "@/lib/api/workspaces";
import { ApiError } from "@/lib/api/problem";
import type { InviteDto } from "@/lib/api/types";

// ワークスペースへの招待リンクを発行するモーダル（F-15・OWNER のみ）。
// 発行 API は平文 token / inviteUrl を発行時にのみ返すため、ここで取得した URL をコピーして配る。
export function InviteWorkspaceModal({
  open,
  onClose,
  workspaceId,
  workspaceName,
}: {
  open: boolean;
  onClose: () => void;
  workspaceId: string;
  workspaceName?: string;
}) {
  const [invite, setInvite] = useState<InviteDto | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 開くたびに状態をリセットする（前回発行した URL を引きずらない）。
  useEffect(() => {
    if (open) {
      setInvite(null);
      setCopied(false);
      setError(null);
    }
  }, [open]);

  const mutation = useMutation({
    mutationFn: () => createWorkspaceInvite(workspaceId),
    onSuccess: (data) => {
      setInvite(data);
      setCopied(false);
      setError(null);
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : "招待リンクの発行に失敗しました");
    },
  });

  const close = () => {
    if (mutation.isPending) return;
    onClose();
  };

  const copy = async () => {
    if (!invite) return;
    try {
      await navigator.clipboard.writeText(invite.inviteUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError("クリップボードへのコピーに失敗しました。URL を手動で選択してコピーしてください。");
    }
  };

  const expiresLabel = invite
    ? new Date(invite.expiresAt).toLocaleString("ja-JP", {
        year: "numeric",
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : null;

  return (
    <Modal
      open={open}
      onClose={close}
      title={workspaceName ? `${workspaceName} に招待` : "ワークスペースに招待"}
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={mutation.isPending}>
            閉じる
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            {mutation.isPending
              ? "発行中…"
              : invite
                ? "新しいリンクを発行"
                : "招待リンクを発行"}
          </Button>
        </>
      }
    >
      {!invite ? (
        <p className="text-sm text-gray-600">
          招待リンクを発行すると、リンクを知っている人がこのワークスペースに参加できます。
          リンクには有効期限（既定 7 日間）があります。
        </p>
      ) : (
        <div className="space-y-3">
          <p className="text-sm text-gray-600">
            以下のリンクを共有してください。受け取った人はログイン後にこのワークスペースへ参加できます。
          </p>
          <div className="flex items-center gap-2">
            <div className="flex-1 flex items-center gap-2 px-3 py-2 border border-gray-300 rounded bg-gray-50 min-w-0">
              <LinkIcon size={14} className="text-gray-400 shrink-0" />
              <input
                readOnly
                value={invite.inviteUrl}
                onFocus={(e) => e.currentTarget.select()}
                className="flex-1 bg-transparent text-sm outline-none truncate"
              />
            </div>
            <Button variant="secondary" onClick={copy} className="shrink-0">
              {copied ? (
                <span className="flex items-center gap-1">
                  <Check size={14} /> コピー済み
                </span>
              ) : (
                <span className="flex items-center gap-1">
                  <Copy size={14} /> コピー
                </span>
              )}
            </Button>
          </div>
          {expiresLabel && (
            <p className="text-xs text-gray-500">有効期限: {expiresLabel}</p>
          )}
        </div>
      )}

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
    </Modal>
  );
}
