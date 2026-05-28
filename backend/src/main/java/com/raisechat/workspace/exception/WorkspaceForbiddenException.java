package com.raisechat.workspace.exception;

public class WorkspaceForbiddenException extends RuntimeException {
    public WorkspaceForbiddenException(Long workspaceId, Long userId) {
        super("user is not a member of workspace: workspaceId=" + workspaceId + ", userId=" + userId);
    }
}
