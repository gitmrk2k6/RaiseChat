package com.raisechat.workspace;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByOwnerIdAndDeletedAtIsNull(Long ownerId);

    Optional<Workspace> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            SELECT w FROM Workspace w
            JOIN WorkspaceMember m ON m.workspace = w
            WHERE m.user.id = :userId
              AND m.leftAt IS NULL
              AND w.deletedAt IS NULL
              AND w.id > :cursor
            ORDER BY w.id ASC
            """)
    List<Workspace> findAccessibleByUserIdAfterCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
