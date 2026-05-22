"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Search, HelpCircle, Bell } from "lucide-react";
import { Avatar } from "@/components/ui/Avatar";
import { currentUserId, getUser } from "@/lib/mock/users";
import { ProfileModal } from "@/components/modals/ProfileModal";

export function TopBar() {
  const params = useParams<{ workspaceId: string }>();
  const router = useRouter();
  const me = getUser(currentUserId);

  const [q, setQ] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const wid = params.workspaceId ?? "ws-1";
    router.push(`/workspaces/${wid}/search?q=${encodeURIComponent(q)}`);
  };

  return (
    <>
      <header className="h-11 bg-slack-aubergineDark text-white flex items-center px-3 gap-2 shrink-0 border-b border-slack-divider">
        <div className="flex-1 max-w-2xl mx-auto">
          <form
            onSubmit={onSubmit}
            className="flex items-center bg-white/15 hover:bg-white/20 focus-within:bg-white rounded-md px-3 py-1.5 text-sm text-white focus-within:text-slack-aubergine transition"
          >
            <Search size={14} className="mr-2 opacity-80" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="RaiseTech AI を検索"
              className="bg-transparent outline-none flex-1 placeholder:text-white/60 focus:placeholder:text-slack-aubergine/50"
            />
          </form>
        </div>
        <button className="text-white/80 hover:text-white p-1.5" title="ヘルプ">
          <HelpCircle size={18} />
        </button>
        <button className="text-white/80 hover:text-white p-1.5 relative" title="通知">
          <Bell size={18} />
          <span className="absolute top-0 right-0 w-2 h-2 bg-slack-unread rounded-full" />
        </button>
        <button
          onClick={() => setProfileOpen(true)}
          className="ml-1"
          title="プロフィール"
        >
          <Avatar name={me.displayName} color={me.avatarColor} size="sm" />
        </button>
      </header>

      <ProfileModal open={profileOpen} onClose={() => setProfileOpen(false)} />
    </>
  );
}
