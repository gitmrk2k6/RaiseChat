package com.raisechat.channel.dto;

import com.raisechat.channel.ChannelInvite;

import java.time.OffsetDateTime;

// チャンネル招待リンク発行のレスポンス。
// token（平文）と inviteUrl は発行時にのみ返す（DB にはハッシュしか残らず再取得不可）。
public record ChannelInviteResponse(
        Long id,
        Long channelId,
        String token,
        String inviteUrl,
        OffsetDateTime expiresAt,
        Integer maxUses,
        int usedCount,
        OffsetDateTime createdAt
) {
    public static ChannelInviteResponse from(ChannelInvite invite, String rawToken, String inviteUrl) {
        return new ChannelInviteResponse(
                invite.getId(),
                invite.getChannel().getId(),
                rawToken,
                inviteUrl,
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.getCreatedAt()
        );
    }
}
