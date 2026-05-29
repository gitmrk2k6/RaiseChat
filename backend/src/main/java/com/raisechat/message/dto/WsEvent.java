package com.raisechat.message.dto;

// payload はメッセージ系イベントでは MessageResponse、リアクション系では ReactionResponse。
// 配信エンベロープを共通化するため Object で受ける。
public record WsEvent(
        EventType type,
        Object payload
) {
    public enum EventType {
        MESSAGE_CREATED,
        MESSAGE_EDITED,
        MESSAGE_DELETED,
        REACTION_ADDED,
        REACTION_REMOVED
    }
}
