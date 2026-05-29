package com.raisechat.dm.dto;

public record DmRoomCreationResult(
        DmRoomResponse room,
        boolean created
) {}
