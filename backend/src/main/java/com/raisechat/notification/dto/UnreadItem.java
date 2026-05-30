package com.raisechat.notification.dto;

/**
 * 1 スコープ（チャンネル または DM）の未読数。
 *
 * @param scopeType    "channel" または "dm"
 * @param scopeId      チャンネル ID または DM ルーム ID
 * @param unreadCount  未読メッセージ数
 * @param mentionCount 未読メンション数（明示的な @ のうち未読のもの）。unreadCount の内数。
 */
public record UnreadItem(
        String scopeType,
        Long scopeId,
        long unreadCount,
        long mentionCount
) {
    public static final String SCOPE_CHANNEL = "channel";
    public static final String SCOPE_DM = "dm";
}
