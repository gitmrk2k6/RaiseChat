package com.raisechat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "userId は半角英数字・ハイフン・アンダースコア 3〜32 文字")
        String userId,

        @NotBlank
        @Size(max = 32)
        String displayName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
}
