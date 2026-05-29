package com.raisechat.notification.dto;

/**
 * 1 スコープ（チャンネル または DM）の未読数。
 *
 * @param scopeType   "channel" または "dm"
 * @param scopeId     チャンネル ID または DM ルーム ID
 * @param unreadCount 未読メッセージ数
 */
public record UnreadItem(
        String scopeType,
        Long scopeId,
        long unreadCount
) {
    public static final String SCOPE_CHANNEL = "channel";
    public static final String SCOPE_DM = "dm";
}
