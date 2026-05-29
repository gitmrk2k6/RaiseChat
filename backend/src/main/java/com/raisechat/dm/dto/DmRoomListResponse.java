package com.raisechat.dm.dto;

import java.util.List;

public record DmRoomListResponse(
        List<DmRoomResponse> items,
        String nextCursor,
        boolean hasMore
) {}
