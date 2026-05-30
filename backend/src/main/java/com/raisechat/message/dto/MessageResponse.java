package com.raisechat.message.dto;

import com.raisechat.message.Message;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long channelId,
        Long dmRoomId,
        Long authorId,
        String authorUserId,
        String authorDisplayName,
        String body,
        Long parentMessageId,
        List<Long> mentionedUserIds,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MessageResponse from(Message m) {
        return from(m, List.of());
    }

    public static MessageResponse from(Message m, List<Long> mentionedUserIds) {
        return new MessageResponse(
                m.getId(),
                m.getChannel() != null ? m.getChannel().getId() : null,
                m.getDmRoom() != null ? m.getDmRoom().getId() : null,
                m.getAuthor().getId(),
                m.getAuthor().getUserId(),
                m.getAuthor().getDisplayName(),
                m.getBody(),
                m.getParent() != null ? m.getParent().getId() : null,
                mentionedUserIds,
                m.getEditedAt(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
