package com.raisechat.workspace.exception;

// 対象ユーザーが当該ワークスペースのアクティブメンバーでない場合（キック対象が見つからない）→ 404。
public class WorkspaceMemberNotFoundException extends RuntimeException {
    public WorkspaceMemberNotFoundException(Long workspaceId, Long userId) {
        super("user is not a member of workspace: workspaceId=" + workspaceId + ", userId=" + userId);
    }
}
