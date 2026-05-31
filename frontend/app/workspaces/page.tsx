"use client";

// ログイン後の着地点となるリゾルバページ。
// 所属ワークスペース一覧を取得し、先頭ワークスペースへ誘導する（その先で先頭チャンネルへ）。
// 固定パス（旧 /workspaces/ws-1/...）を廃し、実データから遷移先を解決する。

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { listWorkspaces } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";
import { AuthGuard } from "@/components/auth/AuthGuard";

function WorkspacesResolver() {
  const router = useRouter();

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
      <div className="h-screen flex items-center justify-center text-sm text-gray-500">
        参加しているワークスペースがありません
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
