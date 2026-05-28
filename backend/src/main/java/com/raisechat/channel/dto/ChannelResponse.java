package com.raisechat.channel.dto;

import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelType;

import java.time.OffsetDateTime;

public record ChannelResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        ChannelType type,
        Long createdByUserId,
        OffsetDateTime createdAt
) {
    public static ChannelResponse from(Channel c) {
        return new ChannelResponse(
                c.getId(),
                c.getWorkspace().getId(),
                c.getName(),
                c.getDescription(),
                c.getType(),
                c.getCreatedBy().getId(),
                c.getCreatedAt()
        );
    }
}
