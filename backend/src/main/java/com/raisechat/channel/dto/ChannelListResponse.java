package com.raisechat.channel.dto;

import java.util.List;

public record ChannelListResponse(
        List<ChannelResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
