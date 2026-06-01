"use client";

import { useEffect, useMemo, useRef } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { Search, Hash, MessageSquare } from "lucide-react";
import { listSearchResults, type SearchHit } from "@/lib/api/search";
import { listDmRooms } from "@/lib/api/dm";
import { queryKeys } from "@/lib/api/queryKeys";
import { useAuth } from "@/lib/auth/AuthContext";
import { Avatar } from "@/components/ui/Avatar";
import { formatMessageTime, formatDayLabel } from "@/lib/utils";

export default function SearchPage() {
  const params = useParams<{ workspaceId: string }>();
  const workspaceId = params.workspaceId;
  const searchParams = useSearchParams();
  const q = (searchParams.get("q") ?? "").trim();

  const { user } = useAuth();
  const meId = user ? String(user.id) : null;

  // DM ヒットの相手名は検索 API に含まれないため、DM ルーム一覧から解決する（サイドバーと同じキーでキャッシュ共有）。
  const { data: dmRooms } = useQuery({
    queryKey: queryKeys.dmRooms(workspaceId),
    queryFn: () => listDmRooms(workspaceId),
    staleTime: Infinity,
  });
  const dmPartnerName = useMemo(() => {
    const map = new Map<string, string>();
    for (const room of dmRooms ?? []) {
      const partner =
        room.members?.find((m) => m.id !== meId) ?? room.members?.[0];
      if (partner) map.set(room.id, partner.displayName);
    }
    return map;
  }, [dmRooms, meId]);

  // 検索結果（実 API、id 降順・cursor 無限スクロール）。q 未入力時はリクエストしない。
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
  } = useInfiniteQuery({
    queryKey: queryKeys.search(workspaceId, q),
    queryFn: ({ pageParam }) => listSearchResults(workspaceId, q, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) =>
      lastPage.hasMore ? lastPage.nextCursor : undefined,
    enabled: q.length > 0,
    refetchOnWindowFocus: false,
    staleTime: 30_000,
  });

  const hits = useMemo(
    () => (data?.pages ?? []).flatMap((p) => p.items),
    [data],
  );

  // 下端の sentinel が見えたら次ページを読み込む（チャンネル履歴と逆向きなので独自に張る）。
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el || !hasNextPage) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !isFetchingNextPage) fetchNextPage();
      },
      { rootMargin: "200px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="flex-1 overflow-y-auto bg-slack-bg">
      <div className="max-w-3xl mx-auto px-6 py-6">
        <div className="flex items-center gap-2 text-sm text-gray-700 mb-1">
          <Search size={16} />
          <span>
            検索結果{" "}
            {q && (
              <>
                : <span className="font-bold">&quot;{q}&quot;</span>
                {!isLoading && !isError && (
                  <>
                    （{hits.length}
                    {hasNextPage ? "+" : ""}件）
                  </>
                )}
              </>
            )}
          </span>
        </div>
        {/* tsvector が simple 設定のため日本語は形態素解析されず、空白区切りのトークン単位でのみ一致する。 */}
        <p className="text-xs text-gray-400 mb-4">
          単語はスペース区切りで指定してください（連続した日本語の部分一致には未対応です）。
        </p>

        {q === "" ? (
          <div className="bg-white rounded-lg p-10 text-center text-gray-500">
            上部の検索バーからキーワードを入力してください
          </div>
        ) : isLoading ? (
          <div className="bg-white rounded-lg p-10 text-center text-gray-400">
            検索中…
          </div>
        ) : isError ? (
          <div className="bg-white rounded-lg p-10 text-center text-red-500">
            検索に失敗しました。時間をおいて再度お試しください。
          </div>
        ) : hits.length === 0 ? (
          <div className="bg-white rounded-lg p-10 text-center text-gray-500">
            一致するメッセージは見つかりませんでした
          </div>
        ) : (
          <div className="space-y-2">
            {hits.map((m) => (
              <SearchHitCard
                key={m.messageId}
                hit={m}
                workspaceId={workspaceId}
                dmPartnerName={m.dmRoomId ? dmPartnerName.get(m.dmRoomId) : undefined}
                q={q}
              />
            ))}
            <div ref={sentinelRef} />
            {isFetchingNextPage && (
              <div className="py-4 text-center text-sm text-gray-400">
                読み込み中…
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function SearchHitCard({
  hit,
  workspaceId,
  dmPartnerName,
  q,
}: {
  hit: SearchHit;
  workspaceId: string;
  dmPartnerName?: string;
  q: string;
}) {
  // v1 はトップレベルへのジャンプのみ。返信ヒット（parentId あり）は親メッセージへ寄せる。
  const targetId = hit.parentId ?? hit.messageId;
  const href = hit.channelId
    ? `/workspaces/${workspaceId}/channels/${hit.channelId}?msg=${targetId}`
    : hit.dmRoomId
      ? `/workspaces/${workspaceId}/dm/${hit.dmRoomId}?msg=${targetId}`
      : "#";

  return (
    <Link
      href={href}
      className="block bg-white rounded-lg p-4 hover:shadow-md transition border border-transparent hover:border-gray-200"
    >
      <div className="flex items-center gap-2 text-xs text-gray-600 mb-2">
        {hit.channelName ? (
          <>
            <Hash size={12} />
            <span className="font-bold">{hit.channelName}</span>
          </>
        ) : hit.dmRoomId ? (
          <>
            <MessageSquare size={12} />
            <span className="font-bold">
              {dmPartnerName ? `@${dmPartnerName}` : "ダイレクトメッセージ"}
            </span>
          </>
        ) : null}
        <span className="text-gray-400">·</span>
        <span>
          {formatDayLabel(hit.createdAt)} {formatMessageTime(hit.createdAt)}
        </span>
      </div>
      <div className="flex gap-3">
        <Avatar name={hit.author.displayName} color={hit.author.avatarColor} size="sm" />
        <div className="flex-1 min-w-0">
          <div className="font-bold text-sm text-gray-900">{hit.author.displayName}</div>
          <div className="text-sm text-gray-800 mt-0.5">{highlight(hit.body, q)}</div>
        </div>
      </div>
    </Link>
  );
}

// 一致キーワードを <mark> で強調する。バックエンドの全文検索とは別に、見た目の手がかりとして
// 入力語の素朴な部分一致で色付けする（日本語/英語いずれもそのまま強調できる）。
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
