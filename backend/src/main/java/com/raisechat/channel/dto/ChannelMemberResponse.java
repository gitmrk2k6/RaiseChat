package com.raisechat.channel.dto;

import com.raisechat.channel.ChannelMember;

/**
 * チャンネルのメンバー（KickUserModal / メンバー一覧表示用）。
 * id は user の数値 ID。チャンネルメンバーにロールは無いため WorkspaceMemberResponse とは別 DTO。
 */
public record ChannelMemberResponse(
        Long id,
        String userId,
        String displayName,
        String avatarUrl
) {
    public static ChannelMemberResponse from(ChannelMember m) {
        return new ChannelMemberResponse(
                m.getUser().getId(),
                m.getUser().getUserId(),
                m.getUser().getDisplayName(),
                m.getUser().getAvatarUrl()
        );
    }
}
