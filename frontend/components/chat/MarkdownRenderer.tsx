"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSanitize from "rehype-sanitize";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getWorkspaceMembers } from "@/lib/api/workspaces";
import { queryKeys } from "@/lib/api/queryKeys";

export function MarkdownRenderer({ body }: { body: string }) {
  // @<userId>（backend MentionService が抽出するハンドル）を表示名のメンションピルに変換する。
  // 解決元は実ワークスペースメンバー（react-query で共有キャッシュ・1 回取得）。
  const params = useParams<{ workspaceId: string }>();
  const workspaceId = params.workspaceId;
  const { data: members = [] } = useQuery({
    queryKey: queryKeys.workspaceMembers(workspaceId),
    queryFn: () => getWorkspaceMembers(workspaceId),
    enabled: !!workspaceId,
    staleTime: 30_000,
  });

  const withMentions = body.replace(/@([A-Za-z0-9_-]+)/g, (_, handle) => {
    const member = members.find((m) => m.userId === handle);
    if (!member) return `@${handle}`;
    return `**@${member.displayName}**`;
  });
  return (
    <div className="prose-msg text-[15px] text-gray-900">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
      >
        {withMentions}
      </ReactMarkdown>
    </div>
  );
}
