package com.raisechat.dm;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.dm.dto.CreateDmRoomRequest;
import com.raisechat.dm.dto.DmRoomCreationResult;
import com.raisechat.dm.dto.DmRoomListResponse;
import com.raisechat.dm.dto.DmRoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DmController {

    private final DmService dmService;

    public DmController(DmService dmService) {
        this.dmService = dmService;
    }

    @PostMapping("/api/workspaces/{wsId}/dm/rooms")
    public ResponseEntity<DmRoomResponse> createRoom(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId,
            @Valid @RequestBody CreateDmRoomRequest req
    ) {
        DmRoomCreationResult result = dmService.createOrGetRoom(principal.id(), wsId, req);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.room());
    }

    @GetMapping("/api/workspaces/{wsId}/dm/rooms")
    public DmRoomListResponse listRooms(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return dmService.listMyRooms(principal.id(), wsId, cursor, limit);
    }
}
