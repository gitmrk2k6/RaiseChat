package com.raisechat.notification.dto;

/**
 * WebSocket（/user/queue/notifications）で各クライアントへ届けるユーザー個別イベント。
 * Redis pub/sub（notifications:{userId}）のペイロードとしても用いる。
 *
 * <p>未読更新（UNREAD_*）に加え、F-16 のメンバーシップ剥奪イベント（WORKSPACE_REMOVED /
 * CHANNEL_REMOVED）も同じキューで配信する。剥奪系では unreadCount / mentionCount は使わず
 * 0 を載せ、scopeType / scopeId で「どの WS / チャンネルから外れたか」を表す。受信側は type で
 * 分岐し、未読系は数の上書き、剥奪系はキャッシュ無効化・リダイレクトに用いる。
 *
 * @param type         イベント種別
 * @param scopeType    "channel" / "dm" / "workspace"
 * @param scopeId      チャンネル ID / DM ルーム ID / ワークスペース ID
 * @param unreadCount  更新後の未読数（剥奪系では 0）
 * @param mentionCount 更新後の未読メンション数（unreadCount の内数。剥奪系では 0）
 */
public record NotificationEvent(
        EventType type,
        String scopeType,
        Long scopeId,
        long unreadCount,
        long mentionCount
) {
    public static final String SCOPE_CHANNEL = "channel";
    public static final String SCOPE_WORKSPACE = "workspace";

    public enum EventType {
        UNREAD_UPDATED,
        UNREAD_CLEARED,
        // 対象ユーザーがワークスペースから外れた（キック / WS 削除）。scopeId=workspaceId。
        WORKSPACE_REMOVED,
        // 対象ユーザーが見ていたチャンネルが消えた（チャンネル削除）。scopeId=channelId。
        CHANNEL_REMOVED
    }

    /** WS キック / WS 削除で、対象ユーザーへ「この WS から外れた」を通知するイベント。 */
    public static NotificationEvent workspaceRemoved(Long workspaceId) {
        return new NotificationEvent(EventType.WORKSPACE_REMOVED, SCOPE_WORKSPACE, workspaceId, 0, 0);
    }

    /** チャンネル削除で、メンバーへ「このチャンネルが消えた」を通知するイベント。 */
    public static NotificationEvent channelRemoved(Long channelId) {
        return new NotificationEvent(EventType.CHANNEL_REMOVED, SCOPE_CHANNEL, channelId, 0, 0);
    }
}
