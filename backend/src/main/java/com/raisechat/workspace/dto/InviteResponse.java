package com.raisechat.workspace.dto;

import com.raisechat.workspace.WorkspaceInvite;

import java.time.OffsetDateTime;

// 招待リンク発行のレスポンス。
// token（平文）と inviteUrl は発行時にのみ返す（DB にはハッシュしか残らず再取得不可）。
public record InviteResponse(
        Long id,
        Long workspaceId,
        String token,
        String inviteUrl,
        OffsetDateTime expiresAt,
        Integer maxUses,
        int usedCount,
        OffsetDateTime createdAt
) {
    public static InviteResponse from(WorkspaceInvite invite, String rawToken, String inviteUrl) {
        return new InviteResponse(
                invite.getId(),
                invite.getWorkspace().getId(),
                rawToken,
                inviteUrl,
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.getCreatedAt()
        );
    }
}
