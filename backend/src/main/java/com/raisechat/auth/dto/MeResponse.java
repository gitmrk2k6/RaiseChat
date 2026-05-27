package com.raisechat.auth.dto;

public record MeResponse(
        Long id,
        String userId,
        String displayName,
        String avatarUrl,
        String statusMessage
) {
}
