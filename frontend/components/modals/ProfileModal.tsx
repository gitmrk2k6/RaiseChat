"use client";

import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { useAuth } from "@/lib/auth/AuthContext";
import { avatarColorFor } from "@/lib/api/messages";

export function ProfileModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user, logout } = useAuth();
  // ログイン中の本人（MeResponse）を表示する。色は id から決定論的に補完。
  const color = user ? avatarColorFor(String(user.id)) : "#6B7280";

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
        <Avatar name={user?.displayName ?? "?"} color={color} size="lg" />
        <div>
          <div className="text-lg font-bold">{user?.displayName ?? "-"}</div>
          <div className="text-sm text-gray-500">@{user?.userId ?? "-"}</div>
          {user?.statusMessage && (
            <div className="text-sm text-gray-700 mt-1">{user.statusMessage}</div>
          )}
        </div>
      </div>
    </Modal>
  );
}
