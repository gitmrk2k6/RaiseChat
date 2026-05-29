package com.raisechat.dm.dto;

import com.raisechat.user.User;

public record DmMemberSummary(
        Long id,
        String userId,
        String displayName
) {
    public static DmMemberSummary from(User user) {
        return new DmMemberSummary(user.getId(), user.getUserId(), user.getDisplayName());
    }
}
