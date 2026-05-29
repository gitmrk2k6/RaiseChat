package com.raisechat.message.dto;

public record WsEvent(
        EventType type,
        MessageResponse payload
) {
    public enum EventType {
        MESSAGE_CREATED,
        MESSAGE_EDITED,
        MESSAGE_DELETED
    }
}
