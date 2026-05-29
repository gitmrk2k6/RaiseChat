package com.raisechat.message.dto;

// 付与結果。created=true なら新規付与（201）、false なら既に付与済み（冪等に 200）。
public record ReactionResult(
        ReactionResponse reaction,
        boolean created
) {}
