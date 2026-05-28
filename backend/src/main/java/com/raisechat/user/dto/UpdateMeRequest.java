package com.raisechat.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 1, max = 32, message = "displayName は 1〜32 文字") String displayName,
        @Size(max = 100, message = "statusMessage は 0〜100 文字") String statusMessage
) {
}
