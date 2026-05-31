package com.raisechat.message.dto;

import java.time.OffsetDateTime;

// F-13 検索結果の 1 件。仕様の表示項目（本文 / 投稿者 / チャンネル名 / 投稿日時）に加え、
// 結果クリックでの該当メッセージへのジャンプに必要な id 群（messageId / channelId / dmRoomId /
// parentMessageId）を含める。DM ヒットの場合 channelId / channelName は null。
public record SearchResultItem(
        Long messageId,
        Long channelId,
        String channelName,
        Long dmRoomId,
        Long parentMessageId,
        Long authorId,
        String authorUserId,
        String authorDisplayName,
        String body,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt
) {}
