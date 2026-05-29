package com.raisechat.message.dto;

import java.util.List;

public record MessageListResponse(
        List<MessageResponse> items,
        String nextCursor,
        boolean hasMore
) {}
