"use client";

// ログイン後の着地点となるリゾルバページ。
// 所属ワークスペース一覧を取得し、先頭ワークスペースへ誘導する（その先で先頭チャンネルへ）。
// 固定パス（旧 /workspaces/ws-1/...）を廃し、実データから遷移先を解決する。

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { listWorkspaces } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { Button } from "@/components/ui/Button";
import { CreateWorkspaceModal } from "@/components/modals/CreateWorkspaceModal";

function WorkspacesResolver() {
  const router = useRouter();
  const [createOpen, setCreateOpen] = useState(false);

  const { data: workspaces, isLoading } = useQuery({
    queryKey: queryKeys.workspaces,
    queryFn: listWorkspaces,
  });

  const firstWorkspaceId = workspaces?.[0]?.id;

  useEffect(() => {
    if (firstWorkspaceId) {
      router.replace(`/workspaces/${firstWorkspaceId}`);
    }
  }, [firstWorkspaceId, router]);

  if (!isLoading && workspaces && workspaces.length === 0) {
    return (
      <div className="h-screen flex flex-col items-center justify-center gap-4 text-center px-4">
        <div>
          <p className="text-base font-bold text-gray-800">ようこそ！</p>
          <p className="text-sm text-gray-500 mt-1">
            まだワークスペースがありません。最初のワークスペースを作成しましょう。
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>ワークスペースを作成</Button>
        <CreateWorkspaceModal open={createOpen} onClose={() => setCreateOpen(false)} />
      </div>
    );
  }

  return (
    <div className="h-screen flex items-center justify-center text-sm text-gray-500">
      読み込み中…
    </div>
  );
}

export default function WorkspacesPage() {
  return (
    <AuthGuard>
      <WorkspacesResolver />
    </AuthGuard>
  );
}
