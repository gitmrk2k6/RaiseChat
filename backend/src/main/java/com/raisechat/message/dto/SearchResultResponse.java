package com.raisechat.message.dto;

import java.util.List;

// F-13 検索結果一覧。ページングは既存のメッセージ一覧（MessageListResponse）と同じ
// id カーソル方式に揃える（nextCursor は最後の要素の messageId）。
public record SearchResultResponse(
        List<SearchResultItem> items,
        String nextCursor,
        boolean hasMore
) {}
