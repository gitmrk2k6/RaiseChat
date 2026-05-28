package com.raisechat.dm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByWorkspaceIdAndUserAIdAndUserBId(Long workspaceId, Long userAId, Long userBId);
}
