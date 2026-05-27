package com.raisechat.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndLeftAtIsNull(Long workspaceId, Long userId);

    List<WorkspaceMember> findByUserIdAndLeftAtIsNull(Long userId);
}
