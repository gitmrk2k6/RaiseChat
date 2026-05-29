package com.raisechat.dm;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByWorkspaceIdAndUserAIdAndUserBId(Long workspaceId, Long userAId, Long userBId);

    @Query("""
            SELECT d FROM DmRoom d
            JOIN FETCH d.userA
            JOIN FETCH d.userB
            WHERE d.id = :id
              AND d.deletedAt IS NULL
            """)
    Optional<DmRoom> findActiveByIdWithUsers(@Param("id") Long id);

    @Query("""
            SELECT d FROM DmRoom d
            JOIN FETCH d.userA
            JOIN FETCH d.userB
            WHERE d.workspace.id = :workspaceId
              AND d.deletedAt IS NULL
              AND (d.userA.id = :userId OR d.userB.id = :userId)
              AND (:cursorId = 0 OR d.id < :cursorId)
            ORDER BY d.id DESC
            """)
    List<DmRoom> findUserRoomsBefore(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
