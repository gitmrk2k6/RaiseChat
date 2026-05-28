package com.raisechat.user.dto;

import com.raisechat.user.User;

public record UserResponse(
        Long id,
        String userId,
        String displayName,
        String avatarUrl,
        String statusMessage
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUserId(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getStatusMessage()
        );
    }
}
