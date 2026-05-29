package com.raisechat.dm.dto;

import com.raisechat.dm.DmRoom;

import java.time.OffsetDateTime;
import java.util.List;

public record DmRoomResponse(
        Long id,
        Long workspaceId,
        List<DmMemberSummary> members,
        OffsetDateTime createdAt
) {
    public static DmRoomResponse from(DmRoom room) {
        return new DmRoomResponse(
                room.getId(),
                room.getWorkspace().getId(),
                List.of(DmMemberSummary.from(room.getUserA()), DmMemberSummary.from(room.getUserB())),
                room.getCreatedAt()
        );
    }
}
