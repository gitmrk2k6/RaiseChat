package com.raisechat.workspace;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.workspace.dto.CreateWorkspaceRequest;
import com.raisechat.workspace.dto.WorkspaceDetailResponse;
import com.raisechat.workspace.dto.WorkspaceListResponse;
import com.raisechat.workspace.dto.WorkspaceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateWorkspaceRequest req
    ) {
        return workspaceService.create(principal.id(), req);
    }

    @GetMapping
    public WorkspaceListResponse listMine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return workspaceService.listMine(principal.id(), cursor, limit);
    }

    @GetMapping("/{wsId}")
    public WorkspaceDetailResponse getDetail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId
    ) {
        return workspaceService.getDetail(principal.id(), wsId);
    }
}
