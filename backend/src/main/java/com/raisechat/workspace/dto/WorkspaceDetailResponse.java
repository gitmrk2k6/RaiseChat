package com.raisechat.workspace.dto;

import com.raisechat.workspace.Workspace;
import com.raisechat.workspace.WorkspaceMember;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkspaceDetailResponse(
        Long id,
        String name,
        String description,
        Long ownerUserId,
        OffsetDateTime createdAt,
        List<WorkspaceMemberResponse> members
) {
    public static WorkspaceDetailResponse from(Workspace w, List<WorkspaceMember> members) {
        return new WorkspaceDetailResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                w.getOwner().getId(),
                w.getCreatedAt(),
                members.stream().map(WorkspaceMemberResponse::from).toList()
        );
    }
}
