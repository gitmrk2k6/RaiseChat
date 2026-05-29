package com.raisechat.notification.dto;

/**
 * POST /api/channels/{id}/read・/api/dm/rooms/{id}/read のリクエスト。
 *
 * @param lastReadMessageId 既読にする位置。null の場合はそのスコープの最新メッセージまで既読扱い。
 */
public record MarkReadRequest(
        Long lastReadMessageId
) {
}
