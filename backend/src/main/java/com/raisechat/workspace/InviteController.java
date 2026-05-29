package com.raisechat.workspace;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.workspace.dto.WorkspaceResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// 招待受諾のエンドポイント。token をパスに含むため /api/workspaces 配下ではなく独立した URL を持つ。
// 例外を ProblemDetail で返すため、本クラスは com.raisechat.workspace パッケージ配下に置く
// （GlobalExceptionHandler の basePackages ホワイトリスト対象）。
@RestController
public class InviteController {

    private final WorkspaceService workspaceService;

    public InviteController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    // 招待を受諾し、呼び出しユーザーを参加させる。参加したワークスペースを返す（200）。
    @PostMapping("/api/invites/{token}/accept")
    public WorkspaceResponse accept(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String token
    ) {
        return workspaceService.acceptInvite(principal.id(), token);
    }
}
