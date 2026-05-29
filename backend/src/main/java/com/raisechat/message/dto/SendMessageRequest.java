package com.raisechat.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(min = 1, max = 4000) String body,
        Long parentMessageId
) {}
