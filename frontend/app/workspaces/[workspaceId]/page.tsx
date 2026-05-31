"use client";

// ワークスペースのインデックス。チャンネル一覧を取得し、先頭チャンネルへ誘導する。
// トークンはクライアント専用（メモリ / localStorage）のため、認証フェッチが必要なこの画面はクライアントで動かす。

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { listChannels } from "@/lib/api/channels";
import { queryKeys } from "@/lib/api/queryKeys";

export default function WorkspaceIndex() {
  const params = useParams<{ workspaceId: string }>();
  const workspaceId = params.workspaceId;
  const router = useRouter();

  const { data: channels, isLoading } = useQuery({
    queryKey: queryKeys.channels(workspaceId),
    queryFn: () => listChannels(workspaceId),
    enabled: !!workspaceId,
  });

  const firstChannelId = channels?.[0]?.id;

  useEffect(() => {
    if (firstChannelId) {
      router.replace(`/workspaces/${workspaceId}/channels/${firstChannelId}`);
    }
  }, [firstChannelId, workspaceId, router]);

  if (!isLoading && channels && channels.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
        参加できるチャンネルがありません
      </div>
    );
  }

  return (
    <div className="flex-1 flex items-center justify-center text-sm text-gray-500">
      読み込み中…
    </div>
  );
}
