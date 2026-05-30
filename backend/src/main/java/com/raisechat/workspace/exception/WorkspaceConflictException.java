package com.raisechat.workspace.exception;

// ワークスペース操作の状態的な衝突（例: OWNER が自分自身をキックしようとした）→ 409。
public class WorkspaceConflictException extends RuntimeException {
    public WorkspaceConflictException(String message) {
        super(message);
    }
}
