package com.raisechat.dm.dto;

import jakarta.validation.constraints.NotNull;

public record CreateDmRoomRequest(
        @NotNull Long partnerUserId
) {}
