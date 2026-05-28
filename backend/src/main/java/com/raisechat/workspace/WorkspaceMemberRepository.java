package com.raisechat.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndLeftAtIsNull(Long workspaceId, Long userId);

    List<WorkspaceMember> findByUserIdAndLeftAtIsNull(Long userId);

    @Query("""
            SELECT m FROM WorkspaceMember m
            JOIN FETCH m.user
            WHERE m.workspace.id = :workspaceId
              AND m.leftAt IS NULL
            ORDER BY m.joinedAt ASC, m.id ASC
            """)
    List<WorkspaceMember> findActiveByWorkspaceIdWithUser(@Param("workspaceId") Long workspaceId);
}
