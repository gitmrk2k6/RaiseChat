"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { users } from "@/lib/mock/users";
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
  const candidates = users.filter((u) => !channel.memberIds.includes(u.id));
  const [selected, setSelected] = useState<string[]>([]);

  const toggle = (id: string) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const submit = () => {
    alert(`#${channel.name} に ${selected.length} 名を招待しました（モック）`);
    setSelected([]);
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`#${channel.name} にメンバーを招待`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>キャンセル</Button>
          <Button onClick={submit} disabled={selected.length === 0}>招待</Button>
        </>
      }
    >
      {candidates.length === 0 ? (
        <p className="text-sm text-gray-600">招待可能なメンバーはいません。</p>
      ) : (
        <div className="space-y-1">
          {candidates.map((u) => (
            <label
              key={u.id}
              className="flex items-center gap-3 px-2 py-2 rounded hover:bg-gray-100 cursor-pointer"
            >
              <input
                type="checkbox"
                checked={selected.includes(u.id)}
                onChange={() => toggle(u.id)}
              />
              <Avatar name={u.displayName} color={u.avatarColor} size="sm" />
              <div>
                <div className="text-sm font-bold">{u.displayName}</div>
                <div className="text-xs text-gray-500">@{u.username}</div>
              </div>
            </label>
          ))}
        </div>
      )}
    </Modal>
  );
}
