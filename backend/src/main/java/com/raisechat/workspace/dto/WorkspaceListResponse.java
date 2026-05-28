package com.raisechat.workspace.dto;

import java.util.List;

public record WorkspaceListResponse(
        List<WorkspaceResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
