package com.raisechat.message.dto;

import java.util.List;

// 1 メッセージ・1 emoji の集計。count はリアクション数、userIds は付与したユーザー（古い順）。
public record ReactionResponse(
        Long messageId,
        String emoji,
        int count,
        List<Long> userIds
) {}
