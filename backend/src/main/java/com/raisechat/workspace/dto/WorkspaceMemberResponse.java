package com.raisechat.workspace.dto;

import com.raisechat.workspace.WorkspaceMember;
import com.raisechat.workspace.WorkspaceRole;

public record WorkspaceMemberResponse(
        Long id,
        String userId,
        String displayName,
        String avatarUrl,
        WorkspaceRole role
) {
    public static WorkspaceMemberResponse from(WorkspaceMember m) {
        return new WorkspaceMemberResponse(
                m.getUser().getId(),
                m.getUser().getUserId(),
                m.getUser().getDisplayName(),
                m.getUser().getAvatarUrl(),
                m.getRole()
        );
    }
}
