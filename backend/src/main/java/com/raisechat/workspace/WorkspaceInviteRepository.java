package com.raisechat.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {

    Optional<WorkspaceInvite> findByTokenHash(String tokenHash);

    // 無効化(revoke)時に、招待 ID がそのワークスペースに属することを保証して取得する。
    // 別ワークスペースの inviteId を指定された場合は空 → 404 として扱う。
    Optional<WorkspaceInvite> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
