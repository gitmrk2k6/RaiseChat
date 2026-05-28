package com.raisechat.workspace.exception;

public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException(Long workspaceId) {
        super("workspace not found: id=" + workspaceId);
    }
}
