package com.raisechat.channel.dto;

import com.raisechat.channel.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
        @NotBlank(message = "name は必須") @Size(min = 1, max = 80, message = "name は 1〜80 文字") String name,
        @Size(max = 255, message = "description は 0〜255 文字") String description,
        @NotNull(message = "type は必須") ChannelType type
) {
}
