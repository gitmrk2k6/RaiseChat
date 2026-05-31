"use client";

import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { currentUserId, getUser } from "@/lib/mock/users";
import { useAuth } from "@/lib/auth/AuthContext";

export function ProfileModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const me = getUser(currentUserId);
  const { logout } = useAuth();

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="プロフィール"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>閉じる</Button>
          <Button
            variant="danger"
            onClick={() => {
              onClose();
              logout();
            }}
          >
            ログアウト
          </Button>
        </>
      }
    >
      <div className="flex items-center gap-4">
        <Avatar name={me.displayName} color={me.avatarColor} size="lg" />
        <div>
          <div className="text-lg font-bold">{me.displayName}</div>
          <div className="text-sm text-gray-500">@{me.username}</div>
          {me.statusMessage && (
            <div className="text-sm text-gray-700 mt-1">{me.statusMessage}</div>
          )}
        </div>
      </div>
      <div className="mt-4 text-sm">
        <div className="flex gap-2">
          <div className="w-24 text-gray-500">メール</div>
          <div>{me.email ?? "-"}</div>
        </div>
        <div className="flex gap-2 mt-1">
          <div className="w-24 text-gray-500">権限</div>
          <div className="capitalize">{me.role ?? "member"}</div>
        </div>
      </div>
    </Modal>
  );
}
