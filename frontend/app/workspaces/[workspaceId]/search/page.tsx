import Link from "next/link";
import { Search, Hash, Lock, MessageSquare } from "lucide-react";
import { getAllMessages } from "@/lib/mock/messages";
import { getChannel } from "@/lib/mock/channels";
import { getDmRoom } from "@/lib/mock/dms";
import { getUser, currentUserId } from "@/lib/mock/users";
import { Avatar } from "@/components/ui/Avatar";
import { formatMessageTime, formatDayLabel } from "@/lib/utils";

export default function SearchPage({
  params,
  searchParams,
}: {
  params: { workspaceId: string };
  searchParams: { q?: string };
}) {
  const q = (searchParams.q ?? "").trim();
  const all = getAllMessages();
  const results = q.length > 0
    ? all.filter((m) => m.body.toLowerCase().includes(q.toLowerCase()))
    : [];

  return (
    <div className="flex-1 overflow-y-auto bg-slack-bg">
      <div className="max-w-3xl mx-auto px-6 py-6">
        <div className="flex items-center gap-2 text-sm text-gray-700 mb-4">
          <Search size={16} />
          <span>
            検索結果 {q && (
              <>
                : <span className="font-bold">&quot;{q}&quot;</span> （{results.length}件）
              </>
            )}
          </span>
        </div>
        {q === "" ? (
          <div className="bg-white rounded-lg p-10 text-center text-gray-500">
            上部の検索バーからキーワードを入力してください
          </div>
        ) : results.length === 0 ? (
          <div className="bg-white rounded-lg p-10 text-center text-gray-500">
            一致するメッセージは見つかりませんでした
          </div>
        ) : (
          <div className="space-y-2">
            {results.map((m) => {
              const author = getUser(m.authorId);
              const ch = m.channelId ? getChannel(m.channelId) : null;
              const dm = m.dmRoomId ? getDmRoom(m.dmRoomId) : null;
              const dmPartner = dm
                ? getUser(dm.memberIds.find((id) => id !== currentUserId) ?? dm.memberIds[0])
                : null;
              const href = ch
                ? `/workspaces/${params.workspaceId}/channels/${ch.id}`
                : dm
                  ? `/workspaces/${params.workspaceId}/dm/${dm.id}`
                  : "#";
              return (
                <Link
                  key={m.id}
                  href={href}
                  className="block bg-white rounded-lg p-4 hover:shadow-md transition border border-transparent hover:border-gray-200"
                >
                  <div className="flex items-center gap-2 text-xs text-gray-600 mb-2">
                    {ch ? (
                      <>
                        {ch.type === "private" ? <Lock size={12} /> : <Hash size={12} />}
                        <span className="font-bold">{ch.name}</span>
                      </>
                    ) : dmPartner ? (
                      <>
                        <MessageSquare size={12} />
                        <span className="font-bold">@{dmPartner.username}</span>
                      </>
                    ) : null}
                    <span className="text-gray-400">·</span>
                    <span>{formatDayLabel(m.createdAt)} {formatMessageTime(m.createdAt)}</span>
                  </div>
                  <div className="flex gap-3">
                    <Avatar name={author.displayName} color={author.avatarColor} size="sm" />
                    <div className="flex-1 min-w-0">
                      <div className="font-bold text-sm text-gray-900">{author.displayName}</div>
                      <div className="text-sm text-gray-800 mt-0.5">
                        {highlight(m.body, q)}
                      </div>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function highlight(text: string, q: string) {
  if (!q) return text;
  const lower = text.toLowerCase();
  const ql = q.toLowerCase();
  const parts: React.ReactNode[] = [];
  let i = 0;
  while (i < text.length) {
    const idx = lower.indexOf(ql, i);
    if (idx < 0) {
      parts.push(text.slice(i));
      break;
    }
    if (idx > i) parts.push(text.slice(i, idx));
    parts.push(
      <mark key={idx} className="bg-yellow-200 px-0.5 rounded">
        {text.slice(idx, idx + q.length)}
      </mark>,
    );
    i = idx + q.length;
  }
  return parts;
}
