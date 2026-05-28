package com.raisechat.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank(message = "name は必須") @Size(min = 1, max = 64, message = "name は 1〜64 文字") String name,
        @Size(max = 255, message = "description は 0〜255 文字") String description
) {
}
