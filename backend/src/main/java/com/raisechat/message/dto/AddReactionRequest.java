package com.raisechat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddReactionRequest(
        @NotBlank @Size(min = 1, max = 32) String emoji
) {}
