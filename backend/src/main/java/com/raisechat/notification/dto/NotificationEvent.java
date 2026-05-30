package com.raisechat.notification.dto;

/**
 * WebSocket（/user/queue/notifications）で各クライアントへ届ける未読更新イベント。
 * Redis pub/sub（notifications:{userId}）のペイロードとしても用いる。
 *
 * @param type         イベント種別
 * @param scopeType    "channel" または "dm"
 * @param scopeId      チャンネル ID または DM ルーム ID
 * @param unreadCount  更新後の未読数
 * @param mentionCount 更新後の未読メンション数（unreadCount の内数）
 */
public record NotificationEvent(
        EventType type,
        String scopeType,
        Long scopeId,
        long unreadCount,
        long mentionCount
) {
    public enum EventType {
        UNREAD_UPDATED,
        UNREAD_CLEARED
    }
}
