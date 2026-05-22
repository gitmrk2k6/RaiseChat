"use client";

import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { getUser, currentUserId } from "@/lib/mock/users";
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
  const others = channel.memberIds.filter((id) => id !== currentUserId).map(getUser);

  const kick = (id: string, name: string) => {
    if (confirm(`${name} を #${channel.name} から削除しますか？`)) {
      alert(`${name} を削除しました（モック）`);
      onClose();
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={`#${channel.name} のメンバー管理`}>
      <p className="text-sm text-gray-600 mb-3">
        メンバーを削除（キック）できます。オーナー権限が必要です。
      </p>
      <div className="space-y-1">
        {others.map((u) => (
          <div key={u.id} className="flex items-center gap-3 px-2 py-2 rounded hover:bg-gray-100">
            <Avatar name={u.displayName} color={u.avatarColor} size="sm" />
            <div className="flex-1">
              <div className="text-sm font-bold">{u.displayName}</div>
              <div className="text-xs text-gray-500">@{u.username}</div>
            </div>
            <Button variant="danger" onClick={() => kick(u.id, u.displayName)}>
              削除
            </Button>
          </div>
        ))}
      </div>
    </Modal>
  );
}
