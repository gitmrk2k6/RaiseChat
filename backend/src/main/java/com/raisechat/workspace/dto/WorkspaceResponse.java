package com.raisechat.workspace.dto;

import com.raisechat.workspace.Workspace;

import java.time.OffsetDateTime;

public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        Long ownerUserId,
        OffsetDateTime createdAt
) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                w.getOwner().getId(),
                w.getCreatedAt()
        );
    }
}
