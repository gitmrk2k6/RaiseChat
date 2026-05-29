package com.raisechat.message.dto;

import com.raisechat.message.Message;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long id,
        Long channelId,
        Long dmRoomId,
        Long authorId,
        String authorUserId,
        String authorDisplayName,
        String body,
        Long parentMessageId,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getChannel() != null ? m.getChannel().getId() : null,
                m.getDmRoom() != null ? m.getDmRoom().getId() : null,
                m.getAuthor().getId(),
                m.getAuthor().getUserId(),
                m.getAuthor().getDisplayName(),
                m.getBody(),
                m.getParent() != null ? m.getParent().getId() : null,
                m.getEditedAt(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
